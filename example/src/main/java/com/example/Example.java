package com.example;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.List;

public class Example {
    private List<String> mutableList;

    // Bug: Useless control flow to next line
    public static void main(String[] args) {
        if (args.length == 1) ;
        System.out.println("Hello, " + args[0]);
    }

    // Bug: Result of integer multiplication cast to long
    public long convertDaysToMilliseconds(int days) {
        return 1000 * 3600 * 24 * days;
    }

    // Bug: Dead store to local variable
    @SuppressFBWarnings("DLS_DEAD_LOCAL_STORE")
    public void foo() {
        int x = 3;
        x = x;
    }

    // Bug: May expose internal representation by returning reference to mutable object
    public List<String> getList() {
        return mutableList;
    }

    public String getMessage() {
        return "Hello World!";
    }

    // Bug: NullPointerException caught
    public boolean hasSpace(String m) {
        try {
            String names[] = m.split(" ");
            return names.length != 1;
        } catch (NullPointerException e) {
            return false;
        }
    }

    // Bug: May expose internal representation by incorporating reference to mutable object
    public void setList(List<String> list) {
        mutableList = list;
    }
}