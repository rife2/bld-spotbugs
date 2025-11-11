package com.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class ExampleTest {
    // Bug: NullPointerException caught
    @Test
    void verifyFoo() {
        try {
            new Example().foo();
        } catch (NullPointerException e) {
            fail("NullPointerException caught");
        }
    }

    @Test
    void verifyHello() {
        assertEquals("Hello World!", new Example().getMessage());
    }
}
