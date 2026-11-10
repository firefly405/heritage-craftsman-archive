package com.qizixuan.heritage.controller;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller public class SpaController {
 @GetMapping({"/","/login","/insights","/artisans","/categories","/works","/announcements","/users","/auctions","/auction-logs"})
 public String index(){return "forward:/index.html";}
}
