package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataTable;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessTableDataProviderTest {

    @Test
    void loadsSeededBusinessTableFromH2() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:db/migration/V3__seed_case5_business_tables.sql")
                .build();
        try {
            BusinessTableDataProvider provider = new BusinessTableDataProvider(new JdbcTemplate(database));

            DataTable orders = provider.loadTable("t_order");

            assertThat(orders.getLogicalName()).isEqualTo("t_order");
            assertThat(orders.getHeaders()).containsExactly("订单ID", "用户ID", "订单状态", "订单金额",
                    "实付金额", "优惠金额", "下单时间", "支付时间", "收货地址", "备注", "数据标记");
            assertThat(orders.getRows()).hasSize(15);
            assertThat(orders.getRows()).anySatisfy(row -> {
                assertThat(row.getPrimaryKey()).isEqualTo("ORD006");
                assertThat(row.value("实付金额")).isEqualTo("520");
                assertThat(row.value("优惠金额")).isEqualTo("30");
            });
        } finally {
            database.shutdown();
        }
    }

    @Test
    void loadsAllBusinessTablesWithMetadata() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:db/migration/V3__seed_case5_business_tables.sql")
                .build();
        try {
            BusinessTableDataProvider provider = new BusinessTableDataProvider(new JdbcTemplate(database));

            Map<String, DataTable> tables = provider.loadAllTables();

            assertThat(tables.keySet()).containsExactly("t_order", "t_order_item", "t_product",
                    "t_payment", "t_inventory_log");
            assertThat(tables.get("t_payment").getRows()).hasSize(15);
            assertThat(tables.get("t_inventory_log").getRows()).hasSize(11);
        } finally {
            database.shutdown();
        }
    }
}
