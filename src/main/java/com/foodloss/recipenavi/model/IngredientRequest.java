package com.foodloss.recipenavi.model;

/**
 * フロントエンドからの食材入力リクエストDTO
 * 
 * 音声認識で取得したテキスト（例: "キャベツが残り1日、豚肉が残り3日"）を受け取る。
 */
public class IngredientRequest {

    /** 音声認識で得られたテキスト */
    private String speechText;

    // デフォルトコンストラクタ（Jacksonデシリアライズ用）
    public IngredientRequest() {}

    public IngredientRequest(String speechText) {
        this.speechText = speechText;
    }

    public String getSpeechText() {
        return speechText;
    }

    public void setSpeechText(String speechText) {
        this.speechText = speechText;
    }
}
