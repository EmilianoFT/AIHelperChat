# AI Helper Chat Eclipse Plugin

AI Helper Chat es un complemento **gratuito y de código abierto** para Eclipse IDE que conecta tu workspace con distintos modelos de IA. No depende de portales propietarios ni de un backend de facturación; basta con proporcionar tus propias credenciales por proveedor.

## Características clave
- Compatibilidad con Ollama, OpenAI, Gemini, Qwen y DeepSeek.
- Vista de chat con streaming, historial reciente y render de Markdown/código.
- Lectura opcional de archivos/proyectos vía acciones `[ACTION:READ_*]` dentro de la respuesta *(en desarrollo, aún no disponible en esta versión).* 
- Lectura y almacenamiento de credenciales mediante el panel de configuración del plugin, que persiste los valores en las preferencias del workspace.

## Requisitos
- Eclipse 2023-12 o superior con SWT.
- JDK 17+ (también funciona con 21).
- API keys personales para los proveedores que quieras usar.

## Instalación rápida
1. Clona el repositorio y abre la carpeta `AI Helper Chat` como proyecto Eclipse PDE.
2. Ejecuta `Run > Eclipse Application` para cargar un runtime workspace con el plugin.
3. Abre la vista `Window > Show View > Other... > AI Helper Chat`.

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

## Configuración de proveedores
Cada proveedor se inicializa leyendo su variable de entorno correspondiente. Si no está definida, la vista mostrará un recordatorio al seleccionarlo. De forma avanzada puedes editar el archivo `.metadata/.plugins/org.eclipse.core.runtime/.settings/com.aihelper.prefs` del workspace runtime para fijar modelos/base URLs personalizados.

## Flujo sugerido de uso
1. Selecciona el proveedor en la barra superior del chat.
2. Elige el modelo descargado/configurado o espera a que se autocompleten las opciones remotas.
3. Escribe tu consulta; presiona `Ctrl+Enter` para enviar.
4. Copia/pega la respuesta con formato o solicita lecturas usando las acciones soportadas.

## Licencia y contribuciones
El código se distribuye bajo la [licencia MIT](TERMS.md), por lo que puedes reutilizarlo en proyectos personales o comerciales sin costos. Las contribuciones y mejoras son bienvenidas mediante issues o pull requests.
