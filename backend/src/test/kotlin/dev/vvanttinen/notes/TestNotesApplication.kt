package dev.vvanttinen.notes

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<NotesApplication>().with(TestcontainersConfiguration::class).run(*args)
}
