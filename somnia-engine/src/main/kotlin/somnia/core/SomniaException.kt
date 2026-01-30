package somnia.core

class SomniaException(
    val errorName: String,
    val errorCode: String,
    message: String
) : RuntimeException(message)
