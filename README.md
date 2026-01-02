# AI Helper Chat Eclipse Plugin

AI Helper Chat es un complemento **gratuito y de código abierto** para Eclipse IDE que conecta tu workspace con distintos modelos de IA. No depende de portales propietarios ni de un backend de facturación; basta con proporcionar tus propias credenciales por proveedor.

## Características clave
- Compatibilidad con Ollama, OpenAI, Gemini, Qwen y DeepSeek.
- Vista de chat con streaming, historial reciente y render de Markdown/código.
- Lectura opcional de archivos/proyectos vía acciones `[ACTION:READ_*]` dentro de la respuesta.
- Lectura de credenciales desde variables de entorno (`OPENAI_API_KEY`, `GEMINI_API_KEY`, `QWEN_API_KEY`, `DEEPSEEK_API_KEY`) con fallback opcional al `IPreferenceStore` del workspace.

## Requisitos
- Eclipse 2023-12 o superior con SWT.
- JDK 17+ (también funciona con 21).
- API keys personales para los proveedores que quieras usar.

## Instalación rápida
1. Clona el repositorio y abre la carpeta `AI Helper Chat` como proyecto Eclipse PDE.
2. Ejecuta `Run > Eclipse Application` para cargar un runtime workspace con el plugin.
3. Abre la vista `Window > Show View > Other... > AI Helper Chat`.
4. Define tus variables de entorno (`OPENAI_API_KEY`, `GEMINI_API_KEY`, `QWEN_API_KEY`, `DEEPSEEK_API_KEY`) antes de lanzar Eclipse. Ollama funciona sin API key si el daemon local responde.

## Configuración de proveedores
Cada proveedor se inicializa leyendo su variable de entorno correspondiente. Si no está definida, la vista mostrará un recordatorio al seleccionarlo. De forma avanzada puedes editar el archivo `.metadata/.plugins/org.eclipse.core.runtime/.settings/com.aihelper.prefs` del workspace runtime para fijar modelos/base URLs personalizados.

## Flujo sugerido de uso
1. Selecciona el proveedor en la barra superior del chat.
2. Elige el modelo descargado/configurado o espera a que se autocompleten las opciones remotas.
3. Escribe tu consulta; presiona `Ctrl+Enter` para enviar.
4. Copia/pega la respuesta con formato o solicita lecturas usando las acciones soportadas.

## Licencia y contribuciones
El código se distribuye bajo la [licencia MIT](TERMS.md), por lo que puedes reutilizarlo en proyectos personales o comerciales sin costos. Las contribuciones y mejoras son bienvenidas mediante issues o pull requests.
