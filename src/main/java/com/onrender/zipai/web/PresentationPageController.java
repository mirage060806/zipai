package com.onrender.zipai.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PresentationPageController {
    @GetMapping({"/presentation", "/presentation/"})
    public String presentation() {
        return "presentation/index";
    }
}
