package com.callejon9.table.web;

import com.callejon9.table.service.TableService;
import com.callejon9.table.web.dto.CreateTableRequest;
import com.callejon9.table.web.dto.TableResponse;
import com.callejon9.table.web.dto.UpdateTableRequest;
import com.callejon9.table.web.dto.UpdateTableStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public List<TableResponse> list(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return tableService.listTables(includeInactive).stream()
                .map(TableResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TableResponse create(@Valid @RequestBody CreateTableRequest request) {
        return TableResponse.from(tableService.createTable(request.number(), request.capacity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TableResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTableRequest request) {
        return TableResponse.from(tableService.updateTable(id, request.number(), request.capacity()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TableResponse patch(@PathVariable UUID id, @Valid @RequestBody UpdateTableStatusRequest request) {
        return TableResponse.from(tableService.setActive(id, request.active()));
    }
}
