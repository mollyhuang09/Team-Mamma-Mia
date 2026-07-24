package com.studypin.app.ui

/** Converts a stored tag value into a readable label without a lookup map. */
fun String.toTagLabel(): String =
    replace('_', ' ').replaceFirstChar { firstCharacter ->
        if (firstCharacter.isLowerCase()) {
            firstCharacter.titlecase()
        } else {
            firstCharacter.toString()
        }
    }
