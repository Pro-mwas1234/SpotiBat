package com.mwask.bat.compose.util.search

fun interface SearchableFieldExtractor<T> {
    fun getSearchableFields(item: T): Array<String>
}