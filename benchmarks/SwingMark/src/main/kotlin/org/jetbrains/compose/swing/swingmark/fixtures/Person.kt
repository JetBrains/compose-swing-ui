package org.jetbrains.compose.swing.swingmark.fixtures

/** A row of `TableRowTest`'s data, one field per column. */
internal data class Person(
    val first: String,
    val last: String,
    val color: String,
    val number: Int,
    val vegetarian: Boolean,
)

/**
 * `TableRowTest`'s own rows, in its own order: the list it ships, twice over, as the original has it.
 *
 * One statement per row, transcribed as the original's resource bundle holds it.
 */
@Suppress("LongMethod", "MagicNumber")
internal fun swingMarkPeople(): List<Person> =
    listOf(
        Person("Mark", "Andrews", "Red", 2, true),
        Person("Tom", "Ball", "Blue", 99, false),
        Person("Alan", "Chung", "Green", 838, false),
        Person("Jeff", "Dinkins", "Turquois", 8, true),
        Person("Amy", "Fowler", "Yellow", 3, false),
        Person("Brian", "Gerhold", "Green", 0, false),
        Person("James", "Gosling", "Pink", 21, false),
        Person("David", "Karlton", "Red", 1, false),
        Person("Dave", "Kloba", "Yellow", 14, false),
        Person("Peter", "Korn", "Purple", 12, false),
        Person("Phil", "Milne", "Purple", 3, false),
        Person("Dave", "Moore", "Green", 88, false),
        Person("Hans", "Muller", "Maroon", 5, false),
        Person("Rick", "Levenson", "Blue", 2, false),
        Person("Tim", "Prinzing", "Blue", 22, false),
        Person("Chester", "Rose", "Black", 0, false),
        Person("Ray", "Ryan", "Gray", 77, false),
        Person("Georges", "Saab", "Red", 4, false),
        Person("Willie", "Walker", "Phthalo Blue", 4, false),
        Person("Kathy", "Walrath", "Blue", 8, false),
        Person("Arnaud", "Weber", "Green", 44, false),
    ).let { it + it }

/**
 * The same rows as the arrays `DefaultTableModel` takes, one array of cells per row, in column order.
 */
internal fun swingMarkTableData(): Array<Array<Any>> =
    swingMarkPeople()
        .map { arrayOf<Any>(it.first, it.last, it.color, it.number, it.vegetarian) }
        .toTypedArray()
