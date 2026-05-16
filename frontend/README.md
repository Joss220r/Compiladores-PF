# Frontend - SQL Platform

Frontend en Vue.js para enviar querys al backend y mostrar la respuesta estandar.

## Ejecutar localmente

Requisitos:

- Node.js 20+
- npm

```bash
cd frontend
npm install
npm run dev
```

La aplicacion escucha en `http://localhost:5173`.

## Configuracion

Copia `.env.example` a `.env` si necesitas cambiar la URL del backend:

```text
VITE_API_BASE_URL=http://localhost:8080
```

Para Render, `render.yaml` define `VITE_API_BASE_URL` apuntando al servicio del backend.

## Pantalla principal

La vista permite:

- seleccionar motor/base de datos;
- escribir la query;
- validar por HTTP contra `POST /api/validate`;
- mostrar estado valido/invalido;
- mostrar errores, tokens, AST y resultado semantico cuando el backend los devuelve.
