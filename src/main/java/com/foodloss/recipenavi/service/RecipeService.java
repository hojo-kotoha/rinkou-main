package com.foodloss.recipenavi.service;

import com.foodloss.recipenavi.model.Recipe;
import com.foodloss.recipenavi.model.RecipeResponse;
import com.foodloss.recipenavi.model.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * レシピ生成パイプライン統合サービス
 * 
 * 処理フロー:
 * 1. 音声認識テキスト → GeminiServiceでレシピ生成
 * 2. 各調理工程テキスト → PollyServiceでBase64音声生成
 * 3. テキスト＋音声を統合したレスポンスを構築
 * 
 * フロントエンドは1回のAPIコールで全データ（レシピ＋全工程の音声）を受け取れる。
 */
@Service
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);

    private final GeminiService geminiService;
    private final PollyService pollyService;

    public RecipeService(GeminiService geminiService, PollyService pollyService) {
        this.geminiService = geminiService;
        this.pollyService = pollyService;
    }

    /**
     * 音声認識テキストからレシピ＋音声データを含む統合レスポンスを生成する
     * 
     * @param speechText 音声認識で取得したテキスト
     * @return 全レシピ・全工程の音声データを含む統合レスポンス
     */
    public RecipeResponse generateRecipesWithAudio(String speechText) {
        log.info("レシピ生成パイプライン開始: {}", speechText);

        // ステップ1: Gemini APIでレシピを生成
        List<Recipe> recipes = geminiService.generateRecipes(speechText);

        // ステップ2: 各レシピの各工程に対してPollyで音声を生成
        for (Recipe recipe : recipes) {
            log.info("レシピ『{}』の音声生成を開始", recipe.getName());

            for (Step step : recipe.getSteps()) {
                // 工程テキストをPollyでMP3に変換し、Base64を取得
                String audioBase64 = pollyService.synthesizeSpeechToBase64(
                        step.getInstruction());

                // 工程オブジェクトにBase64音声データを紐付け
                step.setAudioBase64(audioBase64);

                log.debug("工程{}の音声生成完了", step.getStepNumber());
            }
        }

        log.info("レシピ生成パイプライン完了: {}品のレシピを生成", recipes.size());

        // ステップ3: 統合レスポンスを構築して返却
        return new RecipeResponse(recipes);
    }
}
