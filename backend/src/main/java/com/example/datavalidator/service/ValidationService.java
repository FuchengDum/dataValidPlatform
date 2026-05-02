package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;
import com.example.datavalidator.domain.DataTable;
import com.example.datavalidator.domain.Evidence;
import com.example.datavalidator.domain.RuleDefinition;
import com.example.datavalidator.domain.Severity;
import com.example.datavalidator.domain.ValidationFinding;
import com.example.datavalidator.domain.WorkbookDataset;
import com.example.datavalidator.exception.BadRequestException;
import com.example.datavalidator.persistence.FindingEvidenceEntity;
import com.example.datavalidator.persistence.ValidationFindingEntity;
import com.example.datavalidator.persistence.ValidationJobEntity;
import com.example.datavalidator.repository.FindingEvidenceRepository;
import com.example.datavalidator.repository.ValidationFindingRepository;
import com.example.datavalidator.repository.ValidationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ValidationService {
    private final ExcelImportService excelImportService;
    private final ValidationJobRepository jobRepository;
    private final ValidationFindingRepository findingRepository;
    private final FindingEvidenceRepository evidenceRepository;
    private final JsonService jsonService;

    public ValidationService(ExcelImportService excelImportService,
                             ValidationJobRepository jobRepository,
                             ValidationFindingRepository findingRepository,
                             FindingEvidenceRepository evidenceRepository,
                             JsonService jsonService) {
        this.excelImportService = excelImportService;
        this.jobRepository = jobRepository;
        this.findingRepository = findingRepository;
        this.evidenceRepository = evidenceRepository;
        this.jsonService = jsonService;
    }

    @Transactional
    public ValidationJobResult validate(String datasetId, boolean enableAiAnalysis) {
        WorkbookDataset dataset = excelImportService.loadDataset(datasetId);
        String jobId = IdFactory.next("job");
        LocalDateTime startedAt = LocalDateTime.now();
        ValidationJobEntity job = new ValidationJobEntity();
        job.setJobId(jobId);
        job.setDatasetId(datasetId);
        job.setStatus("RUNNING");
        job.setEnableAiAnalysis(enableAiAnalysis);
        job.setStartedAt(startedAt);
        jobRepository.save(job);

        List<ValidationFinding> findings = executeRules(dataset);
        for (ValidationFinding finding : findings) {
            saveFinding(jobId, finding);
        }

        LocalDateTime finishedAt = LocalDateTime.now();
        job.setFinishedAt(finishedAt);
        job.setDurationMillis(Duration.between(startedAt, finishedAt).toMillis());
        job.setStatus("COMPLETED");
        jobRepository.save(job);
        return new ValidationJobResult(jobId, "COMPLETED", startedAt, finishedAt);
    }

    public SummaryResult summary(String jobId) {
        ValidationJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BadRequestException("校验任务不存在: " + jobId));
        List<ValidationFindingEntity> findings = findingRepository.findByJobId(jobId);
        Map<String, Long> byTable = findings.stream().collect(Collectors.groupingBy(
                item -> safe(item.getTableName()), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byRuleCategory = findings.stream().collect(Collectors.groupingBy(
                ValidationFindingEntity::getRuleCategory, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> bySeverity = findings.stream().collect(Collectors.groupingBy(
                ValidationFindingEntity::getSeverity, LinkedHashMap::new, Collectors.counting()));

        SummaryResult result = new SummaryResult();
        result.jobId = jobId;
        result.totalRules = 30;
        result.executedRules = 30;
        result.findingCount = findings.size();
        result.criticalCount = count(findings, "CRITICAL");
        result.warningCount = count(findings, "WARNING");
        result.durationMillis = job.getDurationMillis() == null ? 0 : job.getDurationMillis();
        result.byTable = byTable;
        result.byRuleCategory = byRuleCategory;
        result.bySeverity = bySeverity;
        return result;
    }

    public List<ValidationFindingEntity> listFindings(String jobId, String severity, String tableName, String ruleId) {
        return findingRepository.findByJobId(jobId).stream()
                .filter(item -> severity == null || severity.isEmpty() || severity.equals(item.getSeverity()))
                .filter(item -> tableName == null || tableName.isEmpty() || tableName.equals(item.getTableName()))
                .filter(item -> ruleId == null || ruleId.isEmpty() || ruleId.equals(item.getRuleId()))
                .collect(Collectors.toList());
    }

    public FindingDetail detail(String findingId) {
        ValidationFindingEntity finding = findingRepository.findById(findingId)
                .orElseThrow(() -> new BadRequestException("异常不存在: " + findingId));
        FindingDetail detail = new FindingDetail();
        detail.finding = finding;
        detail.evidences = evidenceRepository.findByFindingId(findingId);
        return detail;
    }

    private List<ValidationFinding> executeRules(WorkbookDataset dataset) {
        List<ValidationFinding> findings = new ArrayList<>();
        Map<String, DataTable> tables = dataset.getBusinessTables();
        for (RuleDefinition rule : dataset.getRules()) {
            switch (rule.getRuleId()) {
                case "R001": nonNegative(rule, findings, tables.get("t_order"), "订单金额", "实付金额", "优惠金额"); break;
                case "R002": required(rule, findings, tables.get("t_order"), "用户ID", "订单状态", "下单时间", "收货地址"); break;
                case "R003": numeric(rule, findings, tables.get("t_order"), "订单金额", "实付金额"); break;
                case "R004": discountRatio(rule, findings, tables.get("t_order")); break;
                case "R005": nonZero(rule, findings, tables.get("t_order"), "订单金额"); break;
                case "R006": orderPaidAmount(rule, findings, tables.get("t_order")); break;
                case "R007": productPriceCost(rule, findings, tables.get("t_product")); break;
                case "R008": nonNegative(rule, findings, tables.get("t_product"), "库存数量", "成本价"); break;
                case "R009": listedStock(rule, findings, tables.get("t_product")); break;
                case "R010": nonZero(rule, findings, tables.get("t_product"), "售价"); break;
                case "R011": itemSubtotal(rule, findings, tables.get("t_order_item")); break;
                case "R012": positive(rule, findings, tables.get("t_order_item"), "数量"); break;
                case "R013": nonNegative(rule, findings, tables.get("t_payment"), "支付金额", "退款金额"); break;
                case "R014": paymentNonZero(rule, findings, tables.get("t_payment")); break;
                case "R015": inventoryContinuity(rule, findings, tables.get("t_inventory_log")); break;
                case "R016": positive(rule, findings, tables.get("t_inventory_log"), "变动数量"); break;
                case "R017": orderItemAmount(rule, findings, tables); break;
                case "R018": itemProductExists(rule, findings, tables); break;
                case "R019": itemProductPrice(rule, findings, tables); break;
                case "R020": orderPaymentAmount(rule, findings, tables); break;
                case "R021": orderPaymentStatus(rule, findings, tables); break;
                case "R022": orderInventoryQuantity(rule, findings, tables); break;
                case "R023": paymentOrderExists(rule, findings, tables); break;
                case "R024": paymentOrderUser(rule, findings, tables); break;
                case "R025": orderTime(rule, findings, tables.get("t_order")); break;
                case "R026": orderStatus(rule, findings, tables.get("t_order")); break;
                case "R027": nonNegative(rule, findings, tables.get("t_inventory_log"), "变动后库存"); break;
                case "R028": disabledProductOutbound(rule, findings, tables); break;
                case "R029": duplicatePayment(rule, findings, tables.get("t_payment")); break;
                case "R030": orderItemAmount(rule, findings, tables); break;
                default: break;
            }
        }
        return findings;
    }

    private void required(RuleDefinition rule, List<ValidationFinding> findings, DataTable table, String... fields) {
        if (table == null) return;
        for (DataRow row : table.getRows()) {
            for (String field : fields) {
                if (ValueParsers.isBlank(row.value(field))) {
                    findings.add(finding(rule, table.getLogicalName(), row, field, row.value(field), "非空",
                            field + "不能为空", "FIELD_VALUE"));
                }
            }
        }
    }

    private void numeric(RuleDefinition rule, List<ValidationFinding> findings, DataTable table, String... fields) {
        if (table == null) return;
        for (DataRow row : table.getRows()) {
            for (String field : fields) {
                String value = row.value(field);
                if (!ValueParsers.isBlank(value) && !ValueParsers.decimal(value).isPresent()) {
                    findings.add(finding(rule, table.getLogicalName(), row, field, value, "数值类型",
                            field + "必须为数值", "FIELD_VALUE"));
                }
            }
        }
    }

    private void nonNegative(RuleDefinition rule, List<ValidationFinding> findings, DataTable table, String... fields) {
        if (table == null) return;
        for (DataRow row : table.getRows()) {
            for (String field : fields) {
                Optional<BigDecimal> value = ValueParsers.decimal(row.value(field));
                if (value.isPresent() && value.get().compareTo(BigDecimal.ZERO) < 0) {
                    findings.add(finding(rule, table.getLogicalName(), row, field, row.value(field), ">= 0",
                            field + "不得为负数", "FIELD_VALUE"));
                }
            }
        }
    }

    private void positive(RuleDefinition rule, List<ValidationFinding> findings, DataTable table, String field) {
        if (table == null) return;
        for (DataRow row : table.getRows()) {
            Optional<BigDecimal> value = ValueParsers.decimal(row.value(field));
            if (value.isPresent() && value.get().compareTo(BigDecimal.ZERO) <= 0) {
                findings.add(finding(rule, table.getLogicalName(), row, field, row.value(field), "> 0",
                        field + "必须大于0", "FIELD_VALUE"));
            }
        }
    }

    private void nonZero(RuleDefinition rule, List<ValidationFinding> findings, DataTable table, String field) {
        if (table == null) return;
        for (DataRow row : table.getRows()) {
            Optional<BigDecimal> value = ValueParsers.decimal(row.value(field));
            if (value.isPresent() && value.get().compareTo(BigDecimal.ZERO) == 0) {
                findings.add(finding(rule, table.getLogicalName(), row, field, row.value(field), "非零",
                        field + "不能为零", "FIELD_VALUE"));
            }
        }
    }

    private void discountRatio(RuleDefinition rule, List<ValidationFinding> findings, DataTable table) {
        if (table == null) return;
        for (DataRow row : table.getRows()) {
            Optional<BigDecimal> orderAmount = ValueParsers.decimal(row.value("订单金额"));
            Optional<BigDecimal> discount = ValueParsers.decimal(row.value("优惠金额"));
            if (orderAmount.isPresent() && discount.isPresent()
                    && orderAmount.get().compareTo(BigDecimal.ZERO) > 0
                    && discount.get().compareTo(orderAmount.get().multiply(new BigDecimal("0.5"))) > 0) {
                findings.add(finding(rule, table.getLogicalName(), row, "优惠金额", row.value("优惠金额"),
                        "优惠金额 <= 订单金额 * 50%", "优惠金额比例过高", "CALCULATION"));
            }
        }
    }

    private void orderPaidAmount(RuleDefinition rule, List<ValidationFinding> findings, DataTable table) {
        if (table == null) return;
        for (DataRow row : table.getRows()) {
            Optional<BigDecimal> order = ValueParsers.decimal(row.value("订单金额"));
            Optional<BigDecimal> paid = ValueParsers.decimal(row.value("实付金额"));
            Optional<BigDecimal> discount = ValueParsers.decimal(row.value("优惠金额"));
            if (order.isPresent() && paid.isPresent() && discount.isPresent()) {
                BigDecimal expected = order.get().subtract(discount.get());
                if (paid.get().compareTo(order.get()) > 0 || paid.get().compareTo(expected) != 0) {
                    findings.add(finding(rule, table.getLogicalName(), row, "实付金额", row.value("实付金额"),
                            expected.toPlainString(), "实付金额不满足订单金额与优惠金额关系", "CALCULATION"));
                }
            }
        }
    }

    private void productPriceCost(RuleDefinition rule, List<ValidationFinding> findings, DataTable table) {
        if (table == null) return;
        for (DataRow row : table.getRows()) {
            Optional<BigDecimal> cost = ValueParsers.decimal(row.value("成本价"));
            Optional<BigDecimal> price = ValueParsers.decimal(row.value("售价"));
            if (cost.isPresent() && price.isPresent() && price.get().compareTo(cost.get()) < 0) {
                findings.add(finding(rule, table.getLogicalName(), row, "售价", row.value("售价"),
                        "售价 >= 成本价", "商品售价低于成本价", "CALCULATION"));
            }
        }
    }

    private void listedStock(RuleDefinition rule, List<ValidationFinding> findings, DataTable table) {
        if (table == null) return;
        for (DataRow row : table.getRows()) {
            Optional<BigDecimal> stock = ValueParsers.decimal(row.value("库存数量"));
            if ("上架".equals(row.value("上架状态")) && stock.isPresent() && stock.get().compareTo(BigDecimal.ZERO) <= 0) {
                findings.add(finding(rule, table.getLogicalName(), row, "库存数量", row.value("库存数量"),
                        "> 0", "上架商品库存应大于0", "FIELD_VALUE"));
            }
        }
    }

    private void itemSubtotal(RuleDefinition rule, List<ValidationFinding> findings, DataTable table) {
        if (table == null) return;
        for (DataRow row : table.getRows()) {
            Optional<BigDecimal> price = ValueParsers.decimal(row.value("单价"));
            Optional<BigDecimal> quantity = ValueParsers.decimal(row.value("数量"));
            Optional<BigDecimal> subtotal = ValueParsers.decimal(row.value("小计金额"));
            if (price.isPresent() && quantity.isPresent() && subtotal.isPresent()) {
                BigDecimal expected = price.get().multiply(quantity.get());
                if (subtotal.get().compareTo(expected) != 0) {
                    findings.add(finding(rule, table.getLogicalName(), row, "小计金额", row.value("小计金额"),
                            expected.toPlainString(), "明细小计金额不等于单价乘以数量", "CALCULATION"));
                }
            }
        }
    }

    private void paymentNonZero(RuleDefinition rule, List<ValidationFinding> findings, DataTable table) {
        if (table == null) return;
        for (DataRow row : table.getRows()) {
            Optional<BigDecimal> amount = ValueParsers.decimal(row.value("支付金额"));
            if ("支付成功".equals(row.value("支付状态")) && amount.isPresent() && amount.get().compareTo(BigDecimal.ZERO) == 0) {
                findings.add(finding(rule, table.getLogicalName(), row, "支付金额", row.value("支付金额"),
                        "> 0", "支付成功记录金额不能为零", "FIELD_VALUE"));
            }
        }
    }

    private void inventoryContinuity(RuleDefinition rule, List<ValidationFinding> findings, DataTable table) {
        if (table == null) return;
        for (DataRow row : table.getRows()) {
            Optional<BigDecimal> before = ValueParsers.decimal(row.value("变动前库存"));
            Optional<BigDecimal> quantity = ValueParsers.decimal(row.value("变动数量"));
            Optional<BigDecimal> after = ValueParsers.decimal(row.value("变动后库存"));
            if (before.isPresent() && quantity.isPresent() && after.isPresent()) {
                BigDecimal expected = "入库".equals(row.value("变动类型"))
                        ? before.get().add(quantity.get())
                        : before.get().subtract(quantity.get());
                if (after.get().compareTo(expected) != 0) {
                    findings.add(finding(rule, table.getLogicalName(), row, "变动后库存", row.value("变动后库存"),
                            expected.toPlainString(), "库存变动前后数量不连续", "CALCULATION"));
                }
            }
        }
    }

    private void orderItemAmount(RuleDefinition rule, List<ValidationFinding> findings, Map<String, DataTable> tables) {
        DataTable orders = tables.get("t_order");
        DataTable items = tables.get("t_order_item");
        if (orders == null || items == null) return;
        Map<String, BigDecimal> itemSum = sumBy(items, "订单ID", "小计金额");
        for (DataRow order : orders.getRows()) {
            Optional<BigDecimal> amount = ValueParsers.decimal(order.value("订单金额"));
            BigDecimal sum = itemSum.get(order.value("订单ID"));
            if (amount.isPresent() && sum != null && amount.get().compareTo(sum) != 0) {
                findings.add(finding(rule, "t_order", order, "订单金额", order.value("订单金额"),
                        sum.toPlainString(), "订单金额与明细小计汇总不一致", "AGGREGATION_MISMATCH"));
            }
        }
    }

    private void itemProductExists(RuleDefinition rule, List<ValidationFinding> findings, Map<String, DataTable> tables) {
        DataTable items = tables.get("t_order_item");
        Set<String> productIds = primaryKeys(tables.get("t_product"), "商品ID");
        if (items == null) return;
        for (DataRow item : items.getRows()) {
            if (!productIds.contains(item.value("商品ID"))) {
                findings.add(finding(rule, "t_order_item", item, "商品ID", item.value("商品ID"),
                        "存在于商品表", "订单明细引用的商品不存在", "RELATION_MISSING"));
            }
        }
    }

    private void itemProductPrice(RuleDefinition rule, List<ValidationFinding> findings, Map<String, DataTable> tables) {
        DataTable items = tables.get("t_order_item");
        Map<String, DataRow> products = byKey(tables.get("t_product"), "商品ID");
        if (items == null) return;
        for (DataRow item : items.getRows()) {
            DataRow product = products.get(item.value("商品ID"));
            if (product == null) continue;
            Optional<BigDecimal> itemPrice = ValueParsers.decimal(item.value("单价"));
            Optional<BigDecimal> productPrice = ValueParsers.decimal(product.value("售价"));
            if (itemPrice.isPresent() && productPrice.isPresent() && itemPrice.get().compareTo(productPrice.get()) != 0) {
                findings.add(finding(rule, "t_order_item", item, "单价", item.value("单价"),
                        product.value("售价"), "明细单价与商品售价不一致", "CALCULATION"));
            }
        }
    }

    private void orderPaymentAmount(RuleDefinition rule, List<ValidationFinding> findings, Map<String, DataTable> tables) {
        DataTable orders = tables.get("t_order");
        DataTable payments = tables.get("t_payment");
        if (orders == null || payments == null) return;
        Map<String, BigDecimal> paySum = new HashMap<>();
        for (DataRow payment : payments.getRows()) {
            if ("支付成功".equals(payment.value("支付状态"))) {
                ValueParsers.decimal(payment.value("支付金额")).ifPresent(amount ->
                        paySum.merge(payment.value("订单ID"), amount, BigDecimal::add));
            }
        }
        for (DataRow order : orders.getRows()) {
            Optional<BigDecimal> paid = ValueParsers.decimal(order.value("实付金额"));
            BigDecimal sum = paySum.get(order.value("订单ID"));
            if (paid.isPresent() && sum != null && paid.get().compareTo(sum) != 0) {
                findings.add(finding(rule, "t_order", order, "实付金额", order.value("实付金额"),
                        sum.toPlainString(), "订单实付金额与支付成功金额汇总不一致", "AGGREGATION_MISMATCH"));
            }
        }
    }

    private void orderPaymentStatus(RuleDefinition rule, List<ValidationFinding> findings, Map<String, DataTable> tables) {
        DataTable orders = tables.get("t_order");
        DataTable payments = tables.get("t_payment");
        if (orders == null || payments == null) return;
        Map<String, List<DataRow>> byOrder = groupBy(payments, "订单ID");
        for (DataRow order : orders.getRows()) {
            String status = order.value("订单状态");
            boolean needPayment = "已支付".equals(status) || "已发货".equals(status) || "已完成".equals(status);
            boolean hasSuccess = byOrder.getOrDefault(order.value("订单ID"), new ArrayList<>()).stream()
                    .anyMatch(row -> "支付成功".equals(row.value("支付状态")));
            if (needPayment && !hasSuccess) {
                findings.add(finding(rule, "t_order", order, "订单状态", status,
                        "存在支付成功记录", "已支付链路订单缺少支付成功记录", "RELATION_MISSING"));
            }
        }
    }

    private void orderInventoryQuantity(RuleDefinition rule, List<ValidationFinding> findings, Map<String, DataTable> tables) {
        DataTable items = tables.get("t_order_item");
        DataTable logs = tables.get("t_inventory_log");
        if (items == null || logs == null) return;
        Map<String, BigDecimal> outbound = new HashMap<>();
        for (DataRow log : logs.getRows()) {
            if ("出库".equals(log.value("变动类型"))) {
                String key = log.value("关联订单ID") + "|" + log.value("商品ID");
                ValueParsers.decimal(log.value("变动数量")).ifPresent(amount -> outbound.merge(key, amount, BigDecimal::add));
            }
        }
        for (DataRow item : items.getRows()) {
            String key = item.value("订单ID") + "|" + item.value("商品ID");
            Optional<BigDecimal> quantity = ValueParsers.decimal(item.value("数量"));
            BigDecimal out = outbound.get(key);
            if (quantity.isPresent() && out != null && quantity.get().compareTo(out) != 0) {
                findings.add(finding(rule, "t_order_item", item, "数量", item.value("数量"),
                        out.toPlainString(), "订单明细数量与库存出库数量不一致", "AGGREGATION_MISMATCH"));
            }
        }
    }

    private void paymentOrderExists(RuleDefinition rule, List<ValidationFinding> findings, Map<String, DataTable> tables) {
        DataTable payments = tables.get("t_payment");
        Set<String> orders = primaryKeys(tables.get("t_order"), "订单ID");
        if (payments == null) return;
        for (DataRow payment : payments.getRows()) {
            if (!orders.contains(payment.value("订单ID"))) {
                findings.add(finding(rule, "t_payment", payment, "订单ID", payment.value("订单ID"),
                        "存在于订单表", "支付记录关联的订单不存在", "RELATION_MISSING"));
            }
        }
    }

    private void paymentOrderUser(RuleDefinition rule, List<ValidationFinding> findings, Map<String, DataTable> tables) {
        DataTable payments = tables.get("t_payment");
        Map<String, DataRow> orders = byKey(tables.get("t_order"), "订单ID");
        if (payments == null) return;
        for (DataRow payment : payments.getRows()) {
            DataRow order = orders.get(payment.value("订单ID"));
            if (order != null && !payment.value("用户ID").equals(order.value("用户ID"))) {
                findings.add(finding(rule, "t_payment", payment, "用户ID", payment.value("用户ID"),
                        order.value("用户ID"), "支付用户与订单用户不一致", "FIELD_VALUE"));
            }
        }
    }

    private void orderTime(RuleDefinition rule, List<ValidationFinding> findings, DataTable table) {
        if (table == null) return;
        for (DataRow row : table.getRows()) {
            Optional<LocalDateTime> orderTime = ValueParsers.dateTime(row.value("下单时间"));
            Optional<LocalDateTime> payTime = ValueParsers.dateTime(row.value("支付时间"));
            if (orderTime.isPresent() && payTime.isPresent() && payTime.get().isBefore(orderTime.get())) {
                findings.add(finding(rule, table.getLogicalName(), row, "支付时间", row.value("支付时间"),
                        "支付时间 >= 下单时间", "订单支付时间早于下单时间", "STATUS_TIME_CONFLICT"));
            }
        }
    }

    private void orderStatus(RuleDefinition rule, List<ValidationFinding> findings, DataTable table) {
        if (table == null) return;
        Set<String> allowed = new HashSet<>(java.util.Arrays.asList("待支付", "已支付", "已发货", "已完成", "已取消"));
        for (DataRow row : table.getRows()) {
            if (!allowed.contains(row.value("订单状态"))) {
                findings.add(finding(rule, table.getLogicalName(), row, "订单状态", row.value("订单状态"),
                        allowed.toString(), "订单状态不在允许范围内", "FIELD_VALUE"));
            }
        }
    }

    private void disabledProductOutbound(RuleDefinition rule, List<ValidationFinding> findings, Map<String, DataTable> tables) {
        DataTable logs = tables.get("t_inventory_log");
        Map<String, DataRow> products = byKey(tables.get("t_product"), "商品ID");
        if (logs == null) return;
        for (DataRow log : logs.getRows()) {
            DataRow product = products.get(log.value("商品ID"));
            if (product != null && "下架".equals(product.value("上架状态")) && "出库".equals(log.value("变动类型"))) {
                findings.add(finding(rule, "t_inventory_log", log, "商品ID", log.value("商品ID"),
                        "上架商品", "下架商品不应发生出库", "STATUS_TIME_CONFLICT"));
            }
        }
    }

    private void duplicatePayment(RuleDefinition rule, List<ValidationFinding> findings, DataTable table) {
        if (table == null) return;
        Map<String, List<DataRow>> success = groupBy(table.getRows().stream()
                .filter(row -> "支付成功".equals(row.value("支付状态"))).collect(Collectors.toList()), "订单ID");
        for (Map.Entry<String, List<DataRow>> entry : success.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (DataRow row : entry.getValue()) {
                    findings.add(finding(rule, table.getLogicalName(), row, "订单ID", entry.getKey(),
                            "同一订单仅一条支付成功记录", "同一订单存在重复支付成功记录", "DUPLICATE_CHECK"));
                }
            }
        }
    }

    private ValidationFinding finding(RuleDefinition rule, String tableName, DataRow row, String field,
                                      String actual, String expected, String description, String evidenceType) {
        ValidationFinding finding = new ValidationFinding();
        finding.setFindingId(IdFactory.next("f"));
        finding.setRuleId(rule.getRuleId());
        finding.setRuleName(rule.getRuleName());
        finding.setRuleCategory(rule.getCategory());
        finding.setSeverity(rule.getSeverity());
        finding.setTableName(tableName);
        finding.setRecordKey(row.getPrimaryKey());
        finding.setFieldName(field);
        finding.setActualValue(actual);
        finding.setExpectedValue(expected);
        finding.setDescription(description);
        finding.setScenarioIds(rule.getScenarioIds());
        finding.setReason(row.getPrimaryKey() + " 命中规则 " + rule.getRuleId() + "：" + description + "。");
        finding.setImpact(rule.getSeverity() == Severity.CRITICAL ? "可能影响资金、库存或订单链路核对。" : "建议人工复核，避免后续统计口径偏差。");
        finding.setSuggestion("请核查 " + tableName + " 表记录 " + row.getPrimaryKey() + " 的字段 " + field + "，参考期望值修正或回溯上游造数逻辑。");
        Evidence evidence = new Evidence();
        evidence.setEvidenceType(evidenceType);
        evidence.setTableName(tableName);
        evidence.setRecordKey(row.getPrimaryKey());
        evidence.setFieldName(field);
        evidence.setActualValue(actual);
        evidence.setExpectedValue(expected);
        evidence.setCalculation(description);
        finding.getEvidences().add(evidence);
        return finding;
    }

    private void saveFinding(String jobId, ValidationFinding finding) {
        ValidationFindingEntity entity = new ValidationFindingEntity();
        entity.setFindingId(finding.getFindingId());
        entity.setJobId(jobId);
        entity.setRuleId(finding.getRuleId());
        entity.setRuleName(finding.getRuleName());
        entity.setRuleCategory(finding.getRuleCategory().name());
        entity.setSeverity(finding.getSeverity().name());
        entity.setTableName(finding.getTableName());
        entity.setRecordKey(finding.getRecordKey());
        entity.setFieldName(finding.getFieldName());
        entity.setActualValue(finding.getActualValue());
        entity.setExpectedValue(finding.getExpectedValue());
        entity.setDescription(finding.getDescription());
        entity.setReason(finding.getReason());
        entity.setImpact(finding.getImpact());
        entity.setSuggestion(finding.getSuggestion());
        entity.setScenarioIds(String.join(",", finding.getScenarioIds()));
        findingRepository.save(entity);
        for (Evidence evidence : finding.getEvidences()) {
            FindingEvidenceEntity evidenceEntity = new FindingEvidenceEntity();
            evidenceEntity.setId(IdFactory.next("ev"));
            evidenceEntity.setFindingId(finding.getFindingId());
            evidenceEntity.setEvidenceType(evidence.getEvidenceType());
            evidenceEntity.setTableName(evidence.getTableName());
            evidenceEntity.setRecordKey(evidence.getRecordKey());
            evidenceEntity.setFieldName(evidence.getFieldName());
            evidenceEntity.setActualValue(evidence.getActualValue());
            evidenceEntity.setExpectedValue(evidence.getExpectedValue());
            evidenceEntity.setCalculation(evidence.getCalculation());
            evidenceEntity.setRelatedValuesJson(jsonService.write(evidence.getRelatedValues()));
            evidenceRepository.save(evidenceEntity);
        }
    }

    private Map<String, BigDecimal> sumBy(DataTable table, String groupField, String sumField) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (DataRow row : table.getRows()) {
            ValueParsers.decimal(row.value(sumField)).ifPresent(amount -> result.merge(row.value(groupField), amount, BigDecimal::add));
        }
        return result;
    }

    private Set<String> primaryKeys(DataTable table, String keyField) {
        if (table == null) return new HashSet<>();
        return table.getRows().stream().map(row -> row.value(keyField)).collect(Collectors.toSet());
    }

    private Map<String, DataRow> byKey(DataTable table, String keyField) {
        if (table == null) return new HashMap<>();
        Map<String, DataRow> result = new HashMap<>();
        for (DataRow row : table.getRows()) {
            result.put(row.value(keyField), row);
        }
        return result;
    }

    private Map<String, List<DataRow>> groupBy(DataTable table, String keyField) {
        return table == null ? new HashMap<>() : groupBy(table.getRows(), keyField);
    }

    private Map<String, List<DataRow>> groupBy(List<DataRow> rows, String keyField) {
        return rows.stream().collect(Collectors.groupingBy(row -> row.value(keyField)));
    }

    private long count(List<ValidationFindingEntity> findings, String severity) {
        return findings.stream().filter(item -> severity.equals(item.getSeverity())).count();
    }

    private String safe(String value) {
        return value == null || value.isEmpty() ? "UNKNOWN" : value;
    }

    public static class ValidationJobResult {
        private final String jobId;
        private final String status;
        private final LocalDateTime startedAt;
        private final LocalDateTime finishedAt;

        public ValidationJobResult(String jobId, String status, LocalDateTime startedAt, LocalDateTime finishedAt) {
            this.jobId = jobId;
            this.status = status;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
        }

        public String getJobId() { return jobId; }
        public String getStatus() { return status; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public LocalDateTime getFinishedAt() { return finishedAt; }
    }

    public static class SummaryResult {
        public String jobId;
        public int totalRules;
        public int executedRules;
        public int findingCount;
        public long criticalCount;
        public long warningCount;
        public long durationMillis;
        public Map<String, Long> byTable;
        public Map<String, Long> byRuleCategory;
        public Map<String, Long> bySeverity;
    }

    public static class FindingDetail {
        public ValidationFindingEntity finding;
        public List<FindingEvidenceEntity> evidences;
    }
}
