package ru.teasanctuary.hardcore_experiment.config

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import java.util.concurrent.CompletableFuture
import kotlin.reflect.full.findAnnotation

class ConfigFieldArgumentType : CustomArgumentType.Converted<ConfigField, String> {
    override fun getNativeType(): ArgumentType<String> {
        return StringArgumentType.word()
    }

    override fun convert(nativeType: String): ConfigField {
        return HardcoreExperimentConfig.CONFIG_FIELDS.first { it.name == nativeType }.findAnnotation<ConfigField>()!!
    }

    override fun <S : Any?> listSuggestions(
        context: CommandContext<S>, builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        HardcoreExperimentConfig.CONFIG_FIELDS.forEach {
            builder.suggest(it.name)
        }

        return builder.buildFuture()
    }
}