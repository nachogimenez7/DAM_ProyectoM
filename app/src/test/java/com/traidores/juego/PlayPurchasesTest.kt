package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayPurchasesTest {
    @Test fun accountHashMatchesTheServer() {
        // Value produced by accountHash() in functions/src/purchaseService.js.
        assertEquals("29e322bcafeea7443b68f0ff1a70da25c4468049421988ce6b847058e64381d5", PlayPurchases.accountHash("uid-de-prueba"))
    }

    @Test fun catalogMatchesTheServerProducts() {
        assertEquals(listOf("sin_anuncios", "pack_sello_pueblo", "estilo_espacial", "estilo_abismo_real",
            "estilo_forja_infernal", "estilos_tres", "emotes_memes"), PurchaseCatalog.productIds)
    }
}
