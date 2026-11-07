package com.example.api.application.port.out;
import com.example.api.domain.entity.Demo;
import java.util.Optional;
public interface DemoReader { Optional<Demo> read(); }
