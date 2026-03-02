package com.rpeters.cinefintv.core

/**
 * Centralized feature flag keys for remote config.
 * All feature flags should be defined here for easy reference and type safety.
 */
object FeatureFlags {
    /**
     * AI Feature Flags (existing)
     */
    object AI {
        const val ENABLE_AI_FEATURES = "enable_ai_features"
        const val AI_FORCE_PRO_MODEL = "ai_force_pro_model"
        const val AI_PRIMARY_MODEL_NAME = "ai_primary_model_name"
        const val AI_PRO_MODEL_NAME = "ai_pro_model_name"
        const val AI_PRIMARY_MODEL_TEMPERATURE = "ai_primary_model_temperature"
        const val AI_PRO_MODEL_TEMPERATURE = "ai_pro_model_temperature"
        const val AI_PRIMARY_MODEL_MAX_TOKENS = "ai_primary_model_max_tokens"
        const val AI_PRO_MODEL_MAX_TOKENS = "ai_pro_model_max_tokens"
        const val AI_SUMMARIES = "ai_summaries"
        const val AI_CHAT_SYSTEM_PROMPT = "ai_chat_system_prompt"
        const val AI_SUMMARY_PROMPT_TEMPLATE = "ai_summary_prompt_template"
        const val AI_MOOD_ANALYSIS_PROMPT_TEMPLATE = "ai_mood_analysis_prompt_template"
        const val AI_PERSON_BIO_PROMPT_TEMPLATE = "ai_person_bio_prompt_template"
        const val AI_THEME_EXTRACTION_PROMPT_TEMPLATE = "ai_theme_extraction_prompt_template"
        const val AI_WHY_LOVE_THIS_PROMPT_TEMPLATE = "ai_why_love_this_prompt_template"
        const val AI_MOOD_COLLECTIONS_PROMPT_TEMPLATE = "ai_mood_collections_prompt_template"
        const val AI_SMART_RECOMMENDATIONS_PROMPT_TEMPLATE = "ai_smart_recommendations_prompt_template"
        const val AI_SEARCH_KEYWORD_LIMIT = "ai_search_keyword_limit"
        const val AI_RECOMMENDATION_COUNT = "ai_recommendation_count"
        const val AI_HISTORY_CONTEXT_SIZE = "ai_history_context_size"

        // Quick Win AI Features
        const val AI_PERSON_BIO = "ai_person_bio"
        const val AI_PERSON_BIO_CONTEXT_SIZE = "ai_person_bio_context_size"
        const val AI_THEMATIC_ANALYSIS = "ai_thematic_analysis"
        const val AI_THEME_EXTRACTION_LIMIT = "ai_theme_extraction_limit"
        const val AI_WHY_YOULL_LOVE_THIS = "ai_why_youll_love_this"
        const val AI_MOOD_COLLECTIONS = "ai_mood_collections"
        const val AI_SMART_RECOMMENDATIONS = "ai_smart_recommendations"
        const val AI_SMART_RECOMMENDATIONS_LIMIT = "ai_smart_recommendations_limit"
    }

    /**
     * Experimental & Utility Flags
     */
    object Experimental {
        /** Enable/disable cast reliability fixes from CAST_SYSTEM_UNIFIED_PLAN phase rollout */
        const val ENABLE_CAST_FIX_PATH = "enable_cast_fix_path"

        /** Enable/disable video player gestures (tap/drag) */
        const val ENABLE_VIDEO_PLAYER_GESTURES = "enable_video_player_gestures"

        /** Enable/disable AI-based quality recommendations */
        const val ENABLE_QUALITY_RECOMMENDATIONS = "enable_quality_recommendations"

        /** Custom seek interval for video player in milliseconds */
        const val VIDEO_PLAYER_SEEK_INTERVAL_MS = "video_player_seek_interval_ms"

        /** Toggle visibility of transcoding diagnostics tool */
        const val SHOW_TRANSCODING_DIAGNOSTICS = "show_transcoding_diagnostics"

        /** Experimental player buffer size in milliseconds */
        const val EXPERIMENTAL_PLAYER_BUFFER_MS = "experimental_player_buffer_ms"
    }
}
