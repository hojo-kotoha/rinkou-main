package com.foodloss.recipenavi.model;

import java.util.List;

/**
 * レシピデータモデル
 * 
 * 1つの料理に対応するレシピ情報を保持する。
 * Gemini APIの構造化出力から生成され、Pollyの音声データと統合される。
 */
public class Recipe {

    /** 料理名（例: "豚肉とキャベツの味噌炒め"） */
    private String name;

    /** レシピの簡単な説明 */
    private String description;

    /** 消費期限が近い食材リスト */
    private List<Ingredient> expiringIngredients;

    /** 別途必要な調味料・食材リスト */
    private List<String> additionalIngredients;

    /** 調理工程リスト（音声データ付き） */
    private List<Step> steps;

    // デフォルトコンストラクタ（Jacksonデシリアライズ用）
    public Recipe() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Ingredient> getExpiringIngredients() {
        return expiringIngredients;
    }

    public void setExpiringIngredients(List<Ingredient> expiringIngredients) {
        this.expiringIngredients = expiringIngredients;
    }

    public List<String> getAdditionalIngredients() {
        return additionalIngredients;
    }

    public void setAdditionalIngredients(List<String> additionalIngredients) {
        this.additionalIngredients = additionalIngredients;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }
}
