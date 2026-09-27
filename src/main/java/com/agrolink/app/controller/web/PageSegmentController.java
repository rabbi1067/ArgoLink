package com.agrolink.app.controller.web;

import com.agrolink.app.exception.ResourceNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Set;

@Controller
@RequestMapping("/templates/pages")
public class PageSegmentController {

    private static final Set<String> ALLOWED = Set.of(
            "overview", "operations", "control", "produce", "orders", "weather", "assistant",
            "invoices", "messages", "users", "analytics", "settings"
    );

    @GetMapping("/{page}")
    public String segment(@PathVariable String page) {
        if (!ALLOWED.contains(page)) {
            throw new ResourceNotFoundException("Page", "name", page);
        }
        return "pages/" + page;
    }
}