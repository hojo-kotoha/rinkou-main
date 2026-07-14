package com.foodloss.recipenavi.controller;

import com.foodloss.recipenavi.model.IngredientRequest;
import com.foodloss.recipenavi.model.RecipeResponse;
import com.foodloss.recipenavi.service.RecipeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * レシピ生成REST APIコントローラー
 * 
 * フロントエンドからの食材テキストを受け取り、
 * AI生成レシピ＋音声データの統合レスポンスを返却する。
 */
@RestController
@RequestMapping("/api/recipes")
@CrossOrigin(origins = "*")  // 開発時はCORS全許可（本番ではドメイン指定推奨）
public class RecipeController {

    private static final Logger log = LoggerFactory.getLogger(RecipeController.class);

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    /**
     * POST /api/recipes/generate
     * 
     * 食材の音声認識テキストからレシピ＋音声を生成する。
     * 
     * @param request 音声認識テキストを含むリクエスト
     * @return レシピ＋全工程Base64音声データの統合レスポンス
     */
    @PostMapping("/generate")
    public ResponseEntity<RecipeResponse> generateRecipes(
            @RequestBody IngredientRequest request) {

        log.info("レシピ生成リクエスト受信: {}", request.getSpeechText());

        // 入力バリデーション
        if (request.getSpeechText() == null || request.getSpeechText().trim().isEmpty()) {
            log.warn("空のテキストが送信されました");
            return ResponseEntity.badRequest().build();
        }

        try {
            // パイプライン実行（Gemini → Polly → 統合）
            RecipeResponse response = recipeService.generateRecipesWithAudio(
                    request.getSpeechText());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("レシピ生成でエラーが発生", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
