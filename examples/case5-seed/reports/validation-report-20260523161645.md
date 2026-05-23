# 通用数据验证报告

- 总规则数: 30
- 已执行规则数: 30
- 异常总数: 68
- 严重异常: 60
- 警告异常: 8
- 校验耗时: 93 ms

| 规则 | 等级 | 表 | 主键 | 字段 | 描述 |
|---|---|---|---|---|---|
|R001|CRITICAL|t_order|ORD008|订单金额|订单金额不得为负数|
|R001|CRITICAL|t_order|ORD008|实付金额|实付金额不得为负数|
|R002|CRITICAL|t_order|ORD007|用户ID|用户ID不能为空|
|R002|CRITICAL|t_order|ORD015|收货地址|收货地址不能为空|
|R003|CRITICAL|t_order|ORD014|订单金额|订单金额必须为数值|
|R004|WARNING|t_order|ORD008|优惠金额|行表达式条件不成立|
|R005|CRITICAL|t_order|ORD012|订单金额|行表达式条件不成立|
|R006|CRITICAL|t_order|ORD005|实付金额|行表达式条件不成立|
|R006|CRITICAL|t_order|ORD006|实付金额|行表达式条件不成立|
|R006|CRITICAL|t_order|ORD010|实付金额|行表达式条件不成立|
|R006|CRITICAL|t_order|ORD014|实付金额|行表达式条件不成立|
|R007|WARNING|t_product|PRD007|售价|行表达式条件不成立|
|R008|CRITICAL|t_product|PRD008|库存数量|库存数量不得为负数|
|R009|WARNING|t_product|PRD008|库存数量|行表达式条件不成立|
|R009|WARNING|t_product|PRD009|库存数量|行表达式条件不成立|
|R010|CRITICAL|t_product|PRD013|成本价|行表达式条件不成立|
|R011|CRITICAL|t_order_item|ITM007|小计金额|行表达式条件不成立|
|R011|CRITICAL|t_order_item|ITM012|小计金额|行表达式条件不成立|
|R012|CRITICAL|t_order_item|ITM010|数量|行表达式条件不成立|
|R012|CRITICAL|t_order_item|ITM012|数量|行表达式条件不成立|
|R012|CRITICAL|t_order_item|ITM015|数量|行表达式条件不成立|
|R013|CRITICAL|t_payment|PAY006|支付金额|支付金额不得为负数|
|R014|CRITICAL|t_payment|PAY010|支付金额|行表达式条件不成立|
|R015|CRITICAL|t_inventory_log|LOG006|变动后库存|行表达式条件不成立|
|R015|CRITICAL|t_inventory_log|LOG010|变动后库存|行表达式条件不成立|
|R016|CRITICAL|t_inventory_log|LOG010|变动数量|行表达式条件不成立|
|R017|CRITICAL|t_order|ORD002|订单金额|聚合结果不一致|
|R017|CRITICAL|t_order|ORD003|订单金额|聚合结果不一致|
|R017|CRITICAL|t_order|ORD007|订单金额|聚合结果不一致|
|R017|CRITICAL|t_order|ORD008|订单金额|聚合结果不一致|
|R017|CRITICAL|t_order|ORD009|订单金额|聚合结果不一致|
|R017|CRITICAL|t_order|ORD010|订单金额|聚合结果不一致|
|R017|CRITICAL|t_order|ORD011|订单金额|聚合结果不一致|
|R017|CRITICAL|t_order|ORD013|订单金额|聚合结果不一致|
|R018|CRITICAL|t_order_item|ITM008|商品ID|关联记录不存在|
|R019|WARNING|t_order_item|ITM009|单价|关联断言不成立|
|R019|WARNING|t_order_item|ITM010|单价|关联断言不成立|
|R019|WARNING|t_order_item|ITM011|单价|关联断言不成立|
|R020|CRITICAL|t_order|ORD002|实付金额|聚合结果不一致|
|R020|CRITICAL|t_order|ORD005|实付金额|聚合结果不一致|
|R021|CRITICAL|t_order|ORD007|订单ID|关联记录不存在|
|R021|CRITICAL|t_order|ORD015|订单ID|关联记录不存在|
|R022|CRITICAL|t_order_item|ITM004|订单ID|关联记录不存在|
|R022|CRITICAL|t_order_item|ITM005|数量|关联断言不成立|
|R022|CRITICAL|t_order_item|ITM007|订单ID|关联记录不存在|
|R022|CRITICAL|t_order_item|ITM008|订单ID|关联记录不存在|
|R022|CRITICAL|t_order_item|ITM011|订单ID|关联记录不存在|
|R022|CRITICAL|t_order_item|ITM012|数量|关联断言不成立|
|R022|CRITICAL|t_order_item|ITM014|订单ID|关联记录不存在|
|R022|CRITICAL|t_order_item|ITM015|订单ID|关联记录不存在|
|R023|CRITICAL|t_payment|PAY015|订单ID|关联记录不存在|
|R025|CRITICAL|t_order|ORD009|支付时间|行表达式条件不成立|
|R025|CRITICAL|t_order|ORD011|支付时间|行表达式条件不成立|
|R027|CRITICAL|t_inventory_log|LOG007|变动后库存|变动后库存不得为负数|
|R027|CRITICAL|t_inventory_log|LOG008|变动后库存|变动后库存不得为负数|
|R028|WARNING|t_inventory_log|LOG011|商品ID|不应存在关联记录|
|R029|CRITICAL|t_payment|PAY002|订单ID|分组次数断言不成立|
|R029|CRITICAL|t_payment|PAY014|订单ID|分组次数断言不成立|
|R030|CRITICAL|t_order|ORD002|订单金额|聚合结果不一致|
|R030|CRITICAL|t_order|ORD003|订单金额|聚合结果不一致|
|R030|CRITICAL|t_order|ORD007|订单金额|聚合结果不一致|
|R030|CRITICAL|t_order|ORD008|订单金额|聚合结果不一致|
|R030|CRITICAL|t_order|ORD009|订单金额|聚合结果不一致|
|R030|CRITICAL|t_order|ORD010|订单金额|聚合结果不一致|
|R030|CRITICAL|t_order|ORD011|订单金额|聚合结果不一致|
|R030|CRITICAL|t_order|ORD013|订单金额|聚合结果不一致|
|R030|CRITICAL|t_order|ORD005|日期|聚合来源记录不存在|
|R030|CRITICAL|t_order|ORD015|日期|聚合来源记录不存在|
