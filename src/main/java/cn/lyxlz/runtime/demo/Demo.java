package cn.lyxlz.runtime.demo;

import cn.lyxlz.runtime.anno.RunTime;

import java.util.concurrent.TimeUnit;

public class Demo {
    public static void main(String[] args) {
        test();
    }

    @RunTime
    public static void test() {
        try {
            TimeUnit.SECONDS.sleep(1);
            System.out.println("test1运行完毕");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
