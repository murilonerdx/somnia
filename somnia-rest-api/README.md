# Somnia REST API Example

A complete REST API built purely in Somnia.

## Features

- RESTful endpoints (CRUD)
- JSON request/response
- Schema validation
- Database simulation (in-memory)
- Error handling
- UUID generation

## Running

```bash
somnia run src/main.somnia
```

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/users | List all users |
| GET | /api/users/:id | Get user by ID |
| POST | /api/users | Create user |
| PUT | /api/users/:id | Update user |
| DELETE | /api/users/:id | Delete user |
| GET | /api/health | Health check |
