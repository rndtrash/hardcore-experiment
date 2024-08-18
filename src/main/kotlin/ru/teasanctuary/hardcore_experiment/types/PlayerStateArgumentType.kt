package ru.teasanctuary.hardcore_experiment.types

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import java.util.concurrent.CompletableFuture

class PlayerStateArgumentType : CustomArgumentType.Converted<PlayerState, String> {
    override fun getNativeType(): ArgumentType<String> {
        return StringArgumentType.word()
    }

    override fun convert(nativeType: String): PlayerState {
        return PlayerState.valueOf(nativeType)
    }

    override fun <S : Any?> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        PlayerState.entries.forEach {
            builder.suggest(it.name)
        }

        return builder.buildFuture()
    }
}