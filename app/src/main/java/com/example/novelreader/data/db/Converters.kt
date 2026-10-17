package com.example.novelreader.data.db

import androidx.room.TypeConverter
import com.example.novelreader.data.model.BookType

class Converters {
    @TypeConverter
    fun bookTypeToString(type: BookType): String = type.name

    @TypeConverter
    fun stringToBookType(value: String): BookType = BookType.valueOf(value)
}
