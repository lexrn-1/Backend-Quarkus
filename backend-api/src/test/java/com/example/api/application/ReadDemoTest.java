package com.example.api.application;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import com.example.api.application.usecase.ReadDemoUseCase;
import com.example.api.domain.entity.Demo;
import com.example.api.domain.exception.*;
class ReadDemoTest {
    @Test void returnsMessage() { assertEquals("hola",new ReadDemoUseCase(()->Optional.of(new Demo("hola"))).execute().message()); }
    @Test void missingDocument() { assertThrows(DemoNotFoundException.class,()->new ReadDemoUseCase(Optional::empty).execute()); }
    @Test void unavailableDependency() { assertThrows(DependencyUnavailableException.class,()->new ReadDemoUseCase(()->{throw new DependencyUnavailableException();}).execute()); }
}
