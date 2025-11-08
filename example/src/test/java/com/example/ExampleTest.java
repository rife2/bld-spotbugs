package com.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class ExampleTest {
    @Test
    void verifyHello() {
        assertEquals("Hello World!", new Example().getMessage());
    }

    @Test
    // Bug: NullPointerException caught
    void verifyFoo() {
        try {
            new Example().foo();
        } catch (NullPointerException e) {
            fail("NullPointerException caught");
        }
    }
}
