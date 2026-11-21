package com.neko.music.service

/** 收缩全卷积 MDX 模型的时间维，复用于实时分片推理。 */
internal object MdxModelWindow {
    private data class Field(val number: Int, val wire: Int, val start: Int, val end: Int)

    fun shorten(model: ByteArray, frames: Int): ByteArray {
        require(frames in 1..255)
        val graph = fields(model, 0, model.size).single { it.number == 7 && it.wire == 2 }
        val values = fields(model, graph.start, graph.end).filter { it.number == 11 || it.number == 12 }
        require(values.size == 2)
        for (value in values) {
            val type = fields(model, value.start, value.end).single { it.number == 2 }
            val tensor = fields(model, type.start, type.end).single { it.number == 1 }
            val shape = fields(model, tensor.start, tensor.end).single { it.number == 2 }
            val dimensions = fields(model, shape.start, shape.end).filter { it.number == 1 }
            require(dimensions.size == 4)
            val time = fields(model, dimensions[3].start, dimensions[3].end).single { it.number == 1 && it.wire == 0 }
            require(time.end - time.start == 2 && model[time.start] == 0x80.toByte() && model[time.start + 1] == 2.toByte())
            // 保持 protobuf 外层长度不变，仅把 256 的 varint 原位改为目标帧数。
            model[time.start] = ((frames and 0x7f) or 0x80).toByte()
            model[time.start + 1] = (frames ushr 7).toByte()
        }
        return model
    }

    private fun fields(bytes: ByteArray, start: Int, end: Int): List<Field> {
        var position = start
        fun varint(): Long {
            var value = 0L
            var shift = 0
            while (position < end && shift < 64) {
                val byte = bytes[position++].toInt() and 0xff
                value = value or ((byte and 0x7f).toLong() shl shift)
                if (byte and 0x80 == 0) return value
                shift += 7
            }
            error("模型结构中的整数无效")
        }
        return buildList {
            while (position < end) {
                val key = varint().toInt()
                val wire = key and 7
                val payloadStart: Int
                when (wire) {
                    0 -> { payloadStart = position; varint() }
                    1 -> { payloadStart = position; position += 8 }
                    2 -> {
                        val length = varint()
                        require(length in 0..(end - position).toLong())
                        payloadStart = position
                        position += length.toInt()
                    }
                    5 -> { payloadStart = position; position += 4 }
                    else -> error("模型结构包含不支持的字段")
                }
                require(position <= end && key ushr 3 > 0)
                add(Field(key ushr 3, wire, payloadStart, position))
            }
        }
    }
}
