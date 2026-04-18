# AI Helper Chat Eclipse Plugin

AI Helper Chat es un complemento **gratuito y de código abierto** para Eclipse IDE que conecta tu workspace con distintos modelos de IA. No depende de portales propietarios ni de un backend de facturación; basta con proporcionar tus propias credenciales por proveedor.

## Características clave
- Compatibilidad con Ollama, OpenAI, Gemini, Qwen y DeepSeek.
- Vista de chat con streaming, historial reciente y render de Markdown/código.
- Lectura opcional de archivos/proyectos vía acciones `[ACTION:READ_*]` dentro de la respuesta *(en desarrollo, aún no disponible en esta versión).* 
- Lectura y almacenamiento de credenciales mediante el panel de configuración del plugin, que persiste los valores en las preferencias del workspace.

## Requisitos
- Eclipse 2023-12 o superior con SWT.
- JDK 17 o superior.
- API keys personales para los proveedores que quieras usar.

## Instalación rápida
1. Clona el repositorio y abre la carpeta `AI Helper Chat` como proyecto Eclipse PDE.
2. Ejecuta `Run > Eclipse Application` para cargar un runtime workspace con el plugin.
3. Abre la vista `Window > Show View > Other... > AI Helper Chat`.

## Guía corta (instalación y primer uso)
Esta guía está pensada para usuarios finales que instalan el plugin desde un update site/Marketplace.

1. En Eclipse, abre `Help > Install New Software...`.
2. Pulsa `Add...` y usa la URL del update site publicado.
3. Selecciona `AI Helper`, acepta licencias y reinicia Eclipse cuando lo pida.
4. Abre la vista con `Window > Show View > Other... > AI Helper Chat`.
5. Ve a preferencias (`Window > Preferences > AI Helper`) y configura proveedor/modelo/credenciales.
6. Escribe un prompt corto de prueba y envíalo con `Ctrl+Enter`.

Si no responde, revisa primero: credenciales del proveedor, conectividad de red y modelo configurado.

## Estructura del workspace PDE
El repositorio ya incluye los tres proyectos que exige Eclipse Marketplace para distribuir un bundle completo:
- `com.aihelper`: proyecto plug-in (bundle OSGi) con el código Java y los manifiestos (`plugin.xml`, `MANIFEST.MF`, `build.properties`).
- `com.aihelper.feature`: proyecto Feature independiente que empaqueta el plug-in como artefacto instalable (`feature.xml`, `build.properties`).
- `com.aihelper.updatesite`: proyecto Update Site que publica el Feature dentro de un repositorio p2 (`site.xml`).

Puedes importar los tres proyectos con `File > Import > Existing Projects into Workspace` apuntando a la carpeta raíz del repositorio; Eclipse detectará automáticamente la naturaleza PDE correcta (Plugin, Feature y Update Site) gracias a los archivos `.project` incluidos.

```
Workspace/
├─ com.aihelper                (plugin)
├─ com.aihelper.feature        (feature)
└─ com.aihelper.updatesite     (update site)
```

## Publicación en Eclipse Marketplace
1. **Construye el Feature**: desde el proyecto `com.aihelper.feature` ejecuta `Export > Deployable features` y genera el `.jar` del feature.
2. **Genera el update site**: abre el proyecto `com.aihelper.updatesite`, edita `site.xml` para verificar la URL del feature y usa `Export > Deployable features` o `Build All` dentro del editor para producir el repositorio p2 (`features/` + `plugins/`).
3. **Verifica localmente**: apunta un Eclipse limpio a la carpeta del update site con `Help > Install New Software... > Add... > Local...` para confirmar que la instalación funciona.
4. **Publica en Marketplace**: sube el contenido del update site generado (incluido `site.xml`) al hosting que prefieras y referencia esa URL en el formulario de Marketplace.

## Scripts útiles
La carpeta `scripts/` incluye automatizaciones para repetir el flujo local sin copiar comandos largos:

- `powershell -ExecutionPolicy Bypass -File .\scripts\Rebuild-Plugin.ps1`
	- recompila `com.aihelper`, reconstruye los JARs en `release/` y sincroniza `docs/`.
- `powershell -ExecutionPolicy Bypass -File .\scripts\Verify-PluginArtifacts.ps1`
	- valida que los JARs publicados contengan `plugin.xml`, `ChatView.class`, `LocalWorkspaceRouter.class` y que la metadata p2 (`artifacts.jar` + `content.jar`) exista y sea consistente.
- `powershell -ExecutionPolicy Bypass -File .\scripts\Search-JarText.ps1 -SearchText max_tokens,gpt-4o-mini`
	- busca cadenas ASCII dentro de clases compiladas para inspeccionar constantes incluidas en el binario.

Si tu instalacion de Eclipse no esta en `C:\Users\Emiliano\eclipse\plugins\*`, pasa `-EclipsePluginsPath` o define `AIHELPER_ECLIPSE_PLUGINS_PATH` antes de ejecutar `Rebuild-Plugin.ps1`.

## Configuración de proveedores
Cada proveedor se inicializa leyendo su variable de entorno correspondiente. Si no está definida, la vista mostrará un recordatorio al seleccionarlo. De forma avanzada puedes editar el archivo `.metadata/.plugins/org.eclipse.core.runtime/.settings/com.aihelper.prefs` del workspace runtime para fijar modelos/base URLs personalizados.

## Flujo sugerido de uso
1. Selecciona el proveedor en la barra superior del chat.
2. Elige el modelo descargado/configurado o espera a que se autocompleten las opciones remotas.
3. Escribe tu consulta; presiona `Ctrl+Enter` para enviar.
4. Copia/pega la respuesta con formato o solicita lecturas usando las acciones soportadas.

## Licencia y contribuciones
El código se distribuye bajo la [licencia MIT](TERMS.md), por lo que puedes reutilizarlo en proyectos personales o comerciales sin costos. Las contribuciones y mejoras son bienvenidas mediante issues o pull requests.
