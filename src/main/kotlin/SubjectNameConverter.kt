package main.kotlin

object SubjectNameConverter {
    fun convert(rawName: String): String {
        val names = mapOf(
                "ПИС" to "Право интеллектуальной собственности",
                "АИС" to "Архитектура информационных систем",
                "СЭО" to "Средства электронного обучения",
                "MachOrientProgr" to "Машинно-ориентированное программирование",
                "HighPerfComp" to "Высокопроизводительный вычисления",
                "SysSoft" to "Системное программное обеспечение",
                "RobTech" to "Робототехника"
        )
        return names.getOrElse(rawName) { rawName }
    }
}