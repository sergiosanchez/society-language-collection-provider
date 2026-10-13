# Society Language Collection Provider

Liferay Workspace (DXP 2025.Q1) con un Collection Provider de ejemplo que muestra contenidos web filtrados por:

- **Sociedad**, leída de una cookie y traducida a una categoría. Si la sociedad ya está disponible como dato del usuario (por ejemplo, sincronizada desde LDAP), se puede leer de ahí; el README del módulo explica el cambio.
- **Idioma**: solo aparecen los contenidos traducidos al idioma de navegación, sin usar el idioma por defecto como alternativa.

Evita mantener un segmento y una variación de colección por cada combinación de sociedad e idioma.

El módulo está en [`modules/society-language-collection-provider`](modules/society-language-collection-provider). Su README explica el funcionamiento, la configuración y los puntos a validar.

## Compilar y desplegar

```
./gradlew build
blade gw deploy
```

Es un ejemplo: hay que compilarlo y probarlo en el entorno de destino antes de usarlo.
