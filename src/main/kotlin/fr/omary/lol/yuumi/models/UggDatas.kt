package fr.omary.lol.yuumi.models

import com.beust.klaxon.Json


data class UggDatas(
    @Json(name = "12")
    val world: WorldDatas?
) {
    override fun toString(): String {
        return "UggDatas(world=$world)"
    }
}

data class UggARAMDatas(
    @Json(name = "12")
    val world: WorldARAMDatas?
) {
    override fun toString(): String {
        return "UggARAMDatas(world=$world)"
    }
}

data class WorldDatas(
    @Json(name = "10")
    val platPlus: PositionsSRDatas?
) {
    override fun toString(): String {
        return "WorldDatas(platPlus=$platPlus)"
    }
}

data class WorldARAMDatas(
    @Json(name = "8")
    val aram: PositionsARAMDatas?
) {
    override fun toString(): String {
        return "WorldARAMDatas(aram=$aram)"
    }
}

data class PositionsARAMDatas(
    @Json(name = "6")
    val aramAr: List<Any>?
){
    fun aram(): PositionDatas? =
        PositionDatas(
            "ARAM",
            aramAr?.get(0) as List<Any>?
        )
    override fun toString(): String {
        return "PositionsARAMDatas(aramAr=$aramAr)"
    }
}


data class PositionsSRDatas(
    @Json(name = "1")
    val jungleAr: List<Any>?,
    @Json(name = "2")
    val supportAr: List<Any>?,
    @Json(name = "3")
    val bottomAr: List<Any>?,
    @Json(name = "4")
    val topAr: List<Any>?,
    @Json(name = "5")
    val midAr: List<Any>?
){
    fun jungler(): PositionDatas? =
        PositionDatas(
            "jungle",
            jungleAr?.get(0) as List<Any>?
        )
    fun support(): PositionDatas? =
        PositionDatas(
            "utility",
            supportAr?.get(0) as List<Any>?
        )
    fun bottom(): PositionDatas? =
        PositionDatas(
            "bottom",
            bottomAr?.get(0) as List<Any>?
        )
    fun top(): PositionDatas? =
        PositionDatas(
            "top",
            topAr?.get(0) as List<Any>?
        )
    fun mid(): PositionDatas? =
        PositionDatas(
            "middle",
            midAr?.get(0) as List<Any>?
        )
    fun allRoles() = listOf(jungler(),support(),bottom(),top(),mid())
    override fun toString(): String {
        return "PositionsSRDatas(jungleAr=$jungleAr, supportAr=$supportAr, bottomAr=$bottomAr, topAr=$topAr, midAr=$midAr)"
    }

}



class PositionDatas(name: String, source: List<Any>?) {
    val name: String = name
    val runes: RunesDatas? =
        RunesDatas(
            source?.get(0) as List<Any>?,
            source?.get(8) as List<Any>?
        )
    val summonerSpells: SummonerSpellsDatas? =
        SummonerSpellsDatas(source?.get(1) as List<Any>)
    val startItems: ItemsDatas?  =
        ItemsDatas(source?.get(2) as List<Any>)
    val mainsItems: ItemsDatas?  =
        ItemsDatas(source?.get(3) as List<Any>)
    val skillsOrder: SkillsOrderDatas? =
        SkillsOrderDatas(source?.get(4) as List<Any>)
    val secondaryItems: SecondaryItemsDatas? =
        SecondaryItemsDatas(source?.get(5) as List<Any>)
    val winLoose: WinLooseDatas? =
        WinLooseDatas(source?.get(6) as List<Any>)

    override fun toString(): String {
        return "PositionDatas(runes=$runes, summonerSpells=$summonerSpells, startItems=$startItems, mainsItems=$mainsItems, skillsOrder=$skillsOrder, secondaryItems=$secondaryItems, winLoose=$winLoose)"
    }

}

class RunesDatas(source: List<Any>?, sourceAdditionnal: List<Any>?): WinLooseDatas(source) {
    val masteryA: Int = source!![2] as Int
    val masteryB: Int = source!![3] as Int
    val additionnalMastery: List<Int> =  run {val result = source!![4] as MutableList<Int>
        result.addAll(sourceAdditionnal!![2] as List<Int>)
    result}
    override fun toString(): String {
        return "RunesDatas(masteryA=$masteryA, masteryB=$masteryB, additionnalMastery=$additionnalMastery) ${super.toString()}"
    }


}

class SummonerSpellsDatas(source: List<Any>?) : WinLooseDatas(source) {
    val summonerSpells: List<Int> = source!![2] as List<Int>
    override fun toString(): String {
        return "SummonerSpellsDatas(summonerSpells=$summonerSpells) ${super.toString()}"
    }

}

class ItemsDatas(source: List<Any>?) : WinLooseDatas(source) {
    val itemsId: List<Int> = source!![2] as List<Int>
    override fun toString(): String {
        return "ItemsDatas(itemsId=$itemsId) ${super.toString()}"
    }

}

class ItemDatas(source: List<Any>?) : WinLooseDatas(source) {
    val itemId: Int = source!![2] as Int
    override fun toString(): String {
        return "ItemDatas(itemId=$itemId) ${super.toString()}"
    }
}

class ItemDatas2(source: List<Any>?)  {
    val itemId: Int = source!![0] as Int
    val win: Int = source!![1] as Int
    val total: Int = source!![2] as Int
    override fun toString(): String {
        return "ItemDatas2(itemId=$itemId, win=$win, total=$total)"
    }
}

class SkillsOrderDatas(source: List<Any>?) : WinLooseDatas(source) {
    val skillOrder: List<String> = source!![2] as List<String>
    val skillPriority: String= source!![3] as String
    override fun toString(): String {
        return "SkillsOrderDatas(skillOrder=$skillOrder, skillPriority='$skillPriority') ${super.toString()}"
    }

}

class SecondaryItemsDatas(source: List<Any>?) {
    val fourth: List<ItemDatas2> = (source!![0] as List<Any>).map { s ->
        ItemDatas2(
            s as List<Any>
        )
    }
    val fith: List<ItemDatas2> = (source!![1] as List<Any>).map { s ->
        ItemDatas2(
            s as List<Any>
        )
    }
    val sixth: List<ItemDatas2> = (source!![2] as List<Any>).map { s ->
        ItemDatas2(
            s as List<Any>
        )
    }
    override fun toString(): String {
        return "SecondaryItemsDatas(fourth=$fourth, fith=$fith, sixth=$sixth)"
    }

}

open class WinLooseDatas(source: List<Any>?) {
    val win: Int = source!![0] as Int
    val total: Int = source!![1] as Int
    override fun toString(): String {
        return "WinLooseDatas(win=$win, total=$total)"
    }

}
