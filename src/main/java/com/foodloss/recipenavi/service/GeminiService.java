package com.foodloss.recipenavi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodloss.recipenavi.model.Ingredient;
import com.foodloss.recipenavi.model.Recipe;
import com.foodloss.recipenavi.model.Step;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini 2.5 Flash API連携サービス
 * 
 * 音声認識テキストからAIでレシピを生成する。
 * Structured Outputs（構造化出力）を使用し、確実にパース可能なJSONを取得する。
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final Client client;
    private final String modelName;
    private final ObjectMapper objectMapper;

    /**
     * コンストラクタ
     * 
     * @param apiKey Gemini APIキー（application.propertiesから注入）
     * @param modelName 使用するGeminiモデル名
     */
    public GeminiService(
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model}") String modelName) {
        this.client = Client.builder().apiKey(apiKey).build();
        this.modelName = modelName;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 音声認識テキストからレシピを生成する
     * 
     * @param speechText 音声認識で取得したテキスト（例: "キャベツが残り1日、豚肉が残り3日"）
     * @return 生成されたレシピリスト（音声データなし。audioBase64はnull）
     */
    public List<Recipe> generateRecipes(String speechText) {
        log.info("Gemini APIにレシピ生成をリクエスト: {}", speechText);

        // Geminiへ送信するプロンプトを構築
        String prompt = buildPrompt(speechText);

        // レスポンスのJSONスキーマを定義（Structured Outputs用）
        Schema responseSchema = buildRecipeSchema();

        // Gemini APIの設定（JSON出力を強制）
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .build();

        try {
            // Gemini APIを呼び出し
            GenerateContentResponse response = client.models.generateContent(
                    modelName, prompt, config);

            String jsonResponse = response.text();
            log.debug("Gemini APIレスポンス: {}", jsonResponse);

            // JSONレスポンスをパース
            return parseRecipes(jsonResponse);

        } catch (Exception e) {
            log.error("Gemini APIでエラーが発生しました", e);
            throw new RuntimeException("レシピの生成に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * Geminiに送信するプロンプトを構築
     */
    private String buildPrompt(String speechText) {
        return """
                あなたは食品ロス削減の専門料理家です。
                以下はユーザーが冷蔵庫の食材について音声で入力したテキストです。
                このテキストから食材名と消費期限までの残り日数を読み取り、
                消費期限が迫っている食材を最優先で大量消費するレシピを2品考案してください。

                【ユーザーの音声入力】
                %s

                【ルール】
                - 期限が短い食材ほど多く使うレシピにすること
                - 家庭で一般的な調味料のみ追加で使用すること（特殊な調味料は避ける）
                - 各調理工程は具体的で、料理初心者にもわかりやすい説明にすること
                - 各調理工程は50文字〜100文字程度の簡潔な日本語の文にすること
                - 工程数は3〜6程度にすること
                - 音声読み上げに適した自然な日本語で工程を記述すること
                """.formatted(speechText);
    }

    /**
     * Geminiの構造化出力用JSONスキーマを構築
     * 
     * レスポンスフォーマット:
     * { "recipes": [ { "name": "...", "description": "...", 
     *   "expiringIngredients": [{"name":"...","daysLeft":N}], 
     *   "additionalIngredients": ["..."], 
     *   "steps": [{"stepNumber":N, "instruction":"..."}] } ] }
     */
    private Schema buildRecipeSchema() {
        // 食材スキーマ
        Map<String, Schema> ingredientProps = new HashMap<>();
        ingredientProps.put("name", Schema.builder()
                .type(Type.Known.STRING)
                .description("食材名")
                .build());
        ingredientProps.put("daysLeft", Schema.builder()
                .type(Type.Known.INTEGER)
                .description("消費期限までの残り日数")
                .build());

        Schema ingredientSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(ingredientProps)
                .required(List.of("name", "daysLeft"))
                .build();

        // 工程スキーマ（audioBase64はバックエンドで後付けするため含めない）
        Map<String, Schema> stepProps = new HashMap<>();
        stepProps.put("stepNumber", Schema.builder()
                .type(Type.Known.INTEGER)
                .description("工程番号（1始まり）")
                .build());
        stepProps.put("instruction", Schema.builder()
                .type(Type.Known.STRING)
                .description("調理手順のテキスト")
                .build());

        Schema stepSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(stepProps)
                .required(List.of("stepNumber", "instruction"))
                .build();

        // レシピスキーマ
        Map<String, Schema> recipeProps = new HashMap<>();
        recipeProps.put("name", Schema.builder()
                .type(Type.Known.STRING)
                .description("料理名")
                .build());
        recipeProps.put("description", Schema.builder()
                .type(Type.Known.STRING)
                .description("レシピの簡単な説明")
                .build());
        recipeProps.put("expiringIngredients", Schema.builder()
                .type(Type.Known.ARRAY)
                .items(ingredientSchema)
                .description("消費期限が近い食材リスト")
                .build());
        recipeProps.put("additionalIngredients", Schema.builder()
                .type(Type.Known.ARRAY)
                .items(Schema.builder().type(Type.Known.STRING).build())
                .description("別途必要な調味料リスト")
                .build());
        recipeProps.put("steps", Schema.builder()
                .type(Type.Known.ARRAY)
                .items(stepSchema)
                .description("調理工程リスト")
                .build());

        Schema recipeSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(recipeProps)
                .required(List.of("name", "description", "expiringIngredients",
                        "additionalIngredients", "steps"))
                .build();

        // ルートスキーマ
        Map<String, Schema> rootProps = new HashMap<>();
        rootProps.put("recipes", Schema.builder()
                .type(Type.Known.ARRAY)
                .items(recipeSchema)
                .description("生成されたレシピリスト")
                .build());

        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(rootProps)
                .required(List.of("recipes"))
                .build();
    }

    /**
     * GeminiのJSONレスポンスをRecipeリストにパース
     */
    @SuppressWarnings("unchecked")
    private List<Recipe> parseRecipes(String jsonResponse) {
        try {
            // JSONをMapとしてパース
            Map<String, Object> root = objectMapper.readValue(jsonResponse,
                    new TypeReference<Map<String, Object>>() {});

            List<Map<String, Object>> recipeMaps = 
                    (List<Map<String, Object>>) root.get("recipes");

            List<Recipe> recipes = new ArrayList<>();

            for (Map<String, Object> recipeMap : recipeMaps) {
                Recipe recipe = new Recipe();
                recipe.setName((String) recipeMap.get("name"));
                recipe.setDescription((String) recipeMap.get("description"));

                // 消費期限が近い食材をパース
                List<Map<String, Object>> ingMaps = 
                        (List<Map<String, Object>>) recipeMap.get("expiringIngredients");
                List<Ingredient> ingredients = new ArrayList<>();
                if (ingMaps != null) {
                    for (Map<String, Object> ingMap : ingMaps) {
                        Ingredient ing = new Ingredient();
                        ing.setName((String) ingMap.get("name"));
                        ing.setDaysLeft(((Number) ingMap.get("daysLeft")).intValue());
                        ingredients.add(ing);
                    }
                }
                recipe.setExpiringIngredients(ingredients);

                // 追加調味料をパース
                List<String> additionalIngredients = 
                        (List<String>) recipeMap.get("additionalIngredients");
                recipe.setAdditionalIngredients(
                        additionalIngredients != null ? additionalIngredients : new ArrayList<>());

                // 調理工程をパース（audioBase64はnullのまま）
                List<Map<String, Object>> stepMaps = 
                        (List<Map<String, Object>>) recipeMap.get("steps");
                List<Step> steps = new ArrayList<>();
                if (stepMaps != null) {
                    for (Map<String, Object> stepMap : stepMaps) {
                        Step step = new Step();
                        step.setStepNumber(((Number) stepMap.get("stepNumber")).intValue());
                        step.setInstruction((String) stepMap.get("instruction"));
                        // audioBase64はPollyServiceで後から設定する
                        steps.add(step);
                    }
                }
                recipe.setSteps(steps);

                recipes.add(recipe);
            }

            log.info("{}品のレシピを生成しました", recipes.size());
            return recipes;

        } catch (Exception e) {
            log.error("Geminiレスポンスのパースに失敗: {}", jsonResponse, e);
            throw new RuntimeException("レシピデータのパースに失敗しました", e);
        }
    }
}
