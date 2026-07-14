package com.foodloss.recipenavi.model;

/**
 * 食材データモデル
 * 
 * 消費期限が近い食材の名前と残り日数を保持する。
 */
public class Ingredient {

    /** 食材名（例: "キャベツ"） */
    private String name;

    /** 消費期限までの残り日数 */
    private int daysLeft;

    // デフォルトコンストラクタ（Jacksonデシリアライズ用）
    public Ingredient() {}

    public Ingredient(String name, int daysLeft) {
        this.name = name;
        this.daysLeft = daysLeft;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDaysLeft() {
        return daysLeft;
    }

    public void setDaysLeft(int daysLeft) {
        this.daysLeft = daysLeft;
    }
}
