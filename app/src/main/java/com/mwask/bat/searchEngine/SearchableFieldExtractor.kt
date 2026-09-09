package com.mwask.bat.searchEngine

fun interface SearchableFieldExtractor<T> {
    fun getSearchableFields(item: T): Array<String>
}