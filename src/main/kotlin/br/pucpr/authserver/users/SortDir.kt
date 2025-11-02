package br.pucpr.authserver.users

import br.pucpr.authserver.exception.BadRequestException

enum class SortDir {
    ASC, DESC;

    companion object {
        fun findOrThrow(sortDir: String) =
            SortDir.entries.find { it.name == sortDir.uppercase() }
                ?: throw BadRequestException("Invalid sort dir!")
    }
}