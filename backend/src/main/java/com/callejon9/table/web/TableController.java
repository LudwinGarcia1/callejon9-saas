package com.callejon9.table.web;

import com.callejon9.table.service.TableService;
import com.callejon9.table.web.dto.CreateTableRequest;
import com.callejon9.table.web.dto.TableResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tables")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    public List<TableResponse> list() {
        return tableService.listActiveTables().stream()
                .map(TableResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TableResponse create(@Valid @RequestBody CreateTableRequest request) {
        return TableResponse.from(tableService.createTable(request.number(), request.capacity()));
    }
}
