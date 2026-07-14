package com.foodloss.recipenavi.model;

import java.util.List;

/**
 * レシピ生成APIの統合レスポンスDTO
 * 
 * 複数レシピ（デフォルト2品）を格納し、フロントエンドに返却する。
 * 各レシピには調理工程とBase64音声データが含まれる。
 */
public class RecipeResponse {

    /** 生成されたレシピリスト */
    private List<Recipe> recipes;

    // デフォルトコンストラクタ（Jacksonデシリアライズ用）
    public RecipeResponse() {}

    public RecipeResponse(List<Recipe> recipes) {
        this.recipes = recipes;
    }

    public List<Recipe> getRecipes() {
        return recipes;
    }

    public void setRecipes(List<Recipe> recipes) {
        this.recipes = recipes;
    }
}
