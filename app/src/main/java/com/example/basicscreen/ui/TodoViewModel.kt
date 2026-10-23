package com.example.basicscreen.ui

import androidx.lifecycle.ViewModel
import com.example.basicscreen.model.Priority
import com.example.basicscreen.model.TodoFilter
import com.example.basicscreen.model.TodoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TodoUiState(
    val items: List<TodoItem> = emptyList(),
    val selectedFilter: TodoFilter = TodoFilter.ALL,
    val searchQuery: String = "",
    val isAddDialogVisible: Boolean = false
) {
    val filteredItems: List<TodoItem>
        get() = items.filter { item ->
            val matchesFilter = when (selectedFilter) {
                TodoFilter.ALL -> true
                TodoFilter.ACTIVE -> !item.isCompleted
                TodoFilter.COMPLETED -> item.isCompleted
            }
            val matchesSearch = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }

    val completedCount: Int get() = items.count { it.isCompleted }
    val totalCount: Int get() = items.size
}

class TodoViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        TodoUiState(
            items = listOf(
                TodoItem(
                    title = "Complete Android UI layout",
                    isCompleted = true,
                    priority = Priority.HIGH,
                    category = "Work"
                ),
                TodoItem(
                    title = "Buy fresh fruits & groceries",
                    isCompleted = false,
                    priority = Priority.MEDIUM,
                    category = "Shopping"
                ),
                TodoItem(
                    title = "Schedule dental checkup appointment",
                    isCompleted = false,
                    priority = Priority.LOW,
                    category = "Personal"
                ),
                TodoItem(
                    title = "Review pull requests on GitHub",
                    isCompleted = true,
                    priority = Priority.HIGH,
                    category = "Work"
                ),
                TodoItem(
                    title = "30-minute evening walk",
                    isCompleted = false,
                    priority = Priority.MEDIUM,
                    category = "Health"
                )
            )
        )
    )
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    fun onTaskToggle(id: String) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.id == id) item.copy(isCompleted = !item.isCompleted)
                    else item
                }
            )
        }
    }

    fun onTaskDelete(id: String) {
        _uiState.update { state ->
            state.copy(items = state.items.filterNot { it.id == id })
        }
    }

    fun onAddTask(title: String, priority: Priority, category: String) {
        if (title.isBlank()) return
        val newItem = TodoItem(
            title = title.trim(),
            priority = priority,
            category = category.ifBlank { "General" }
        )
        _uiState.update { state ->
            state.copy(
                items = listOf(newItem) + state.items,
                isAddDialogVisible = false
            )
        }
    }

    fun onFilterChange(filter: TodoFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onShowAddDialog(show: Boolean) {
        _uiState.update { it.copy(isAddDialogVisible = show) }
    }
}
