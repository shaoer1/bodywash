package com.example.controller;

import com.example.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/message")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @GetMapping
    public List<Object> getMessages() {
        return messageService.getMessages();
    }

    @PostMapping
    public void saveMessages(@RequestBody List<Object> messages) {
        messageService.saveMessages(messages);
    }

    @DeleteMapping
    public void clearMessages() {
        messageService.clearMessages();
    }
}
