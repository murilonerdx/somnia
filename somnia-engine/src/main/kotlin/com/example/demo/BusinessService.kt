package com.example.demo

import org.springframework.stereotype.Service

@Service("businessService")
class BusinessService {
    fun processOrder(orderId: String, amount: Double): String {
        println("[SPRING SERVICE] Processing Order #$orderId for $$amount")
        return "SUCCESS: Order $orderId processed"
    }
}
