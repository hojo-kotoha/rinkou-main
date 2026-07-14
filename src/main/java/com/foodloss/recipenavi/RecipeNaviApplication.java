package com.foodloss.recipenavi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AIフードロス削減レシピナビ - メインアプリケーションクラス
 * 
 * 食材の音声入力→AI生成レシピ→音声ナビゲーションを提供するSpring Bootアプリ。
 * フロントエンドはsrc/main/resources/staticから配信される。
 */
@SpringBootApplication
public class RecipeNaviApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecipeNaviApplication.class, args);
    }
}
