package com.example.datavalidator.controller;

import com.example.datavalidator.exception.BadRequestException;
import com.example.datavalidator.web.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {
    @PostMapping("/database-table")
    public ApiResponse<Void> databaseTable() {
        throw new BadRequestException("数据库表输入为P3预留能力，当前MVP请使用Excel上传");
    }

    @PostMapping("/sql-query")
    public ApiResponse<Void> sqlQuery() {
        throw new BadRequestException("SQL查询结果输入为P3预留能力，当前MVP请使用Excel上传");
    }
}
