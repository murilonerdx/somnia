package com.example.demo

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class PureRunner : CommandLineRunner {
    override fun run(vararg args: String?) {
        // Legacy runner. v0.2 uses DynamicSomniaController and SomniaEngine.
    }
}
