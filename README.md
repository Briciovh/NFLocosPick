# 🏈 NFLocos Picks

**¡Demuestra quién manda en el emparrillado!** 🏟️

NFLocos Picks es la aplicación definitiva para los verdaderos fans de la NFL que quieren llevar la emoción de las predicciones al siguiente nivel. Crea tu liga privada, haz tus pronósticos semana tras semana y sube en el ranking hasta convertirte en el MVP de tu grupo.

---

## 🔥 Características Principales

*   **🏆 Ligas Privadas:** Crea tu propio grupo o únete a uno mediante códigos de invitación. La competencia es entre tú y tus amigos.
*   **📅 Calendario en Vivo:** Todos los enfrentamientos de la temporada regular y postemporada, sincronizados con la API de ESPN.
*   **🎯 Picks Semanales:** Elige a tus ganadores antes del kickoff. Los picks se bloquean automáticamente al inicio de cada juego.
*   **📊 Leaderboard Dinámico:** Ranking actualizado en tiempo real. Mira quién domina la liga y quién necesita estudiar más el *playbook*.
*   **📜 Historial de Pronósticos:** Revisa tus aciertos y fallos de semanas pasadas para ajustar tu estrategia.

## 🚀 Stack Tecnológico

Esta app está construida con estándares modernos de desarrollo Android:

*   **Kotlin & Jetpack Compose:** Interfaz moderna y fluida con **Material 3** y soporte para **Dynamic Color**.
*   **Firebase:** 
    *   **Auth:** Login rápido y seguro con Google Sign-In.
    *   **Firestore:** Sincronización de datos en tiempo real.
    *   **Cloud Functions:** Cálculo automático de puntajes.
*   **Arquitectura Limpia (Clean Architecture):** Implementación sólida de capas (Data, Domain, Presentation) siguiendo el patrón **MVVM/MVI**.
*   **Hilt:** Inyección de dependencias para máxima modularidad.
*   **Retrofit:** Integración con la API de ESPN para resultados y calendarios.

## 🛠️ Configuración para Desarrolladores

Si quieres colaborar o probar el proyecto localmente:

1.  **Clona el repositorio:**
    ```bash
    git clone https://github.com/tu-usuario/NFLocosPick.git
    ```
2.  **Firebase Setup:**
    - Crea un proyecto en [Firebase Console](https://console.firebase.google.com/).
    - Registra la app con el package name `com.softeen.nflocospicks`.
    - Descarga `google-services.json` y colócalo en `app/src/`.
3.  **Build:**
    ```bash
    ./gradlew assembleDebug
    ```

---

## 🏈 ¡Que empiece la temporada!

¿Tienes lo necesario para predecir el camino al Super Bowl? Configura tus picks y que gane el mejor.

---
*Desarrollado por y para los NFLocos.*
