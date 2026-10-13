# Society Language Collection Provider

Collection Provider de ejemplo para Liferay DXP que muestra contenidos web (noticias) filtrados por:

- **Sociedad**: se lee de una cookie y se traduce a una categoría (por External Reference Code).
- **Idioma**: solo aparecen los artículos traducidos al idioma de la petición. Si una noticia no está traducida al inglés, no aparece en inglés (no se usa el idioma por defecto como alternativa).

Sustituye la combinación "un segmento por sociedad × idioma + una variación de colección por segmento" por un único proveedor que se usa en el fragmento **Collection Display**. Ambos filtros se aplican en la consulta al índice, así que la paginación y el total son correctos.

Se basa en el mismo patrón que [filter-by-period](https://github.com/sergiosanchez/filter-by-period).

## Cómo funciona

1. Lee la cookie configurada (por defecto `SOCIEDAD`).
2. Convierte el valor en un External Reference Code (ERC) de categoría. Si existe una correspondencia `valor=ERC` en la configuración, usa ese ERC; si no, usa el propio valor de la cookie.
3. Busca la categoría en el sitio actual y, si no está, en el sitio Global.
4. Lanza la búsqueda de `JournalArticle` de la estructura configurada, con:
   - la categoría de sociedad (más las categorías del fragmento Collection Filter, si lo hay);
   - un filtro `exists` sobre `title_<idioma>` (por ejemplo `title_en_US`), que solo existe si el artículo está traducido a ese idioma.
5. Devuelve la última versión aprobada de cada artículo.

Si no hay cookie o no se encuentra la categoría, la colección queda vacía, salvo que se active *Mostrar todo si no hay cookie* (entonces solo se filtra por idioma).

## Configuración

*Panel de control → Configuración del sistema → Contenido web → Noticias por sociedad (cookie) e idioma*

| Campo | Por defecto | Descripción |
|---|---|---|
| Nombre de la cookie de sociedad | `SOCIEDAD` | Cookie de la que se lee la sociedad. Si mañana cambia, solo se toca aquí. |
| Correspondencia valor de cookie / categoría | vacío | Pares `valor=ERC`, por ejemplo `COMPANY_A=sociedad-a`. |
| Clave de la estructura de noticia | `NOTICIA` | Structure Key; se resuelve por clave, no por ID, para que sea estable entre entornos. |
| Prefijo del campo localizado del índice | `title_` | Campo cuya existencia indica que hay traducción. |
| Mostrar todo si no hay cookie | `false` | Comportamiento cuando el usuario no tiene la cookie. |

También se puede desplegar como fichero `.config` en `osgi/configs`:

```
# com.liferay.sample.collection.provider.configuration.CookieLocaleCollectionProviderConfiguration.config
cookieName="SOCIEDAD"
cookieValueMappings=["COMPANY_A=sociedad-a","COMPANY_B=sociedad-b"]
ddmStructureKey="NOTICIA"
languageFieldPrefix="title_"
showAllWhenCookieMissing=B"false"
```

## Preparación del contenido

1. Crear un vocabulario **Sociedad** con una categoría por sociedad.
2. Dar a cada categoría un ERC igual al valor de la cookie (o configurar la correspondencia).
3. Categorizar cada noticia con su sociedad.

## Uso en la página

1. Añadir un fragmento **Collection Display**.
2. Seleccionar como colección *Noticias por sociedad e idioma* (pestaña de Collection Providers).
3. Mapear los campos de la estructura como en cualquier colección.

## Despliegue

Copiar la carpeta en `modules/` de un Liferay Workspace y ejecutar:

```
blade gw deploy
```

Escrito para la API de `dxp-2025.q1.0-lts` (`release.dxp.api`). Es un ejemplo: hay que compilarlo y probarlo en el entorno del cliente antes de usarlo.

## Puntos a validar en el entorno del cliente

- **Campo de idioma en el índice**: comprobar en un documento indexado que `title_<idioma>` solo existe para los idiomas traducidos. No usar `localized_title_<idioma>`, que se rellena con el idioma por defecto como alternativa. El prefijo se cambia por configuración.
- **Ámbito**: se buscan contenidos del sitio actual. Si las noticias están en una Asset Library o en otro sitio, hay que añadir esos `groupIds` en `_buildSearchContext`.
- **Caché**: el resultado depende de la cookie. Si la página o el fragmento pasan por una caché compartida (caché de fragmentos, CDN), esta tiene que variar por la cookie o excluir la página.
- **Servlet API**: el ejemplo usa `javax.servlet`. En versiones que ya usen Jakarta EE, cambiar a `jakarta.servlet`.
- **Firma de `fetchAssetCategoryByExternalReferenceCode`**: en 2025.Q1 es `(String externalReferenceCode, long groupId)`. En versiones antiguas de 7.4 el orden de los parámetros era el inverso.
