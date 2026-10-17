package com.credinoda.service;

import com.credinoda.common.Validators;
import com.credinoda.domain.exception.ClienteNoEncontradoException;
import com.credinoda.domain.exception.DatosInvalidosException;
import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.persistence.repository.ClienteRepository;
import com.credinoda.persistence.repository.CreditoRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Servicio de lógica de negocio para la gestión de clientes prestatarios.
 * Aplica reglas de obligatoriedad, sanitización y unicidad de documento (RN-001, RN-002, RN-003).
 */
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final CreditoRepository creditoRepository;

    public ClienteService(ClienteRepository clienteRepository, CreditoRepository creditoRepository) {
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "ClienteRepository no puede ser nulo");
        this.creditoRepository = creditoRepository;
    }

    public ClienteService(ClienteRepository clienteRepository) {
        this(clienteRepository, null);
    }

    /**
     * Registra un nuevo cliente validando obligatoriedad y unicidad del documento (CU-001, RN-001, RN-002).
     *
     * @param nombre Nombre del cliente.
     * @param telefono Teléfono de contacto.
     * @param documento Documento de identidad único.
     * @return Cliente persistido con su ID asignado.
     * @throws DatosInvalidosException Si algún campo es vacío tras el trim o el documento ya está registrado.
     * @throws RuntimeException Si ocurre un error de persistencia.
     */
    public Cliente crearCliente(String nombre, String telefono, String documento) {
        Validators.ResultadoValidacion valNom = Validators.validarNombreCliente(nombre);
        if (!valNom.valido()) {
            throw new DatosInvalidosException(valNom.mensaje());
        }
        String nombreSanitizado = nombre.strip();

        Validators.ResultadoValidacion valTel = Validators.validarTelefonoCliente(telefono);
        if (!valTel.valido()) {
            throw new DatosInvalidosException(valTel.mensaje());
        }
        String telefonoSanitizado = telefono.strip();

        Validators.ResultadoValidacion valDoc = Validators.validarDocumentoCliente(documento);
        if (!valDoc.valido()) {
            throw new DatosInvalidosException(valDoc.mensaje());
        }
        String documentoSanitizado = documento.strip();

        try {
            Optional<Cliente> existente = clienteRepository.obtenerPorDocumento(documentoSanitizado);
            if (existente.isPresent()) {
                throw new DatosInvalidosException("Ya existe un cliente con el documento: " + documentoSanitizado);
            }

            Cliente nuevoCliente = new Cliente(nombreSanitizado, telefonoSanitizado, documentoSanitizado);
            return clienteRepository.crear(nuevoCliente);
        } catch (SQLException e) {
            throw new DatosInvalidosException("Error al registrar cliente: " + e.getMessage(), e);
        }
    }

    /**
     * Actualiza los datos de un cliente existente (CU-003, RN-001, RN-002).
     * Si un parámetro se pasa como null, se conserva su valor actual.
     *
     * @param id ID del cliente a actualizar.
     * @param nombre Nuevo nombre (o null para mantener el actual).
     * @param telefono Nuevo teléfono (o null para mantener el actual).
     * @param documento Nuevo documento (o null para mantener el actual).
     * @return Cliente actualizado.
     * @throws ClienteNoEncontradoException Si el cliente con dicho ID no existe.
     * @throws DatosInvalidosException Si algún valor enviado queda vacío tras el trim o duplica otro cliente.
     */
    public Cliente actualizarCliente(int id, String nombre, String telefono, String documento) {
        Cliente cliente = obtenerClientePorId(id);

        if (nombre != null) {
            Validators.ResultadoValidacion valNom = Validators.validarNombreCliente(nombre);
            if (!valNom.valido()) {
                throw new DatosInvalidosException(valNom.mensaje());
            }
            cliente.setNombre(nombre.strip());
        }
        if (telefono != null) {
            Validators.ResultadoValidacion valTel = Validators.validarTelefonoCliente(telefono);
            if (!valTel.valido()) {
                throw new DatosInvalidosException(valTel.mensaje());
            }
            cliente.setTelefono(telefono.strip());
        }
        if (documento != null) {
            Validators.ResultadoValidacion valDoc = Validators.validarDocumentoCliente(documento);
            if (!valDoc.valido()) {
                throw new DatosInvalidosException(valDoc.mensaje());
            }
            String nuevoDoc = documento.strip();
            if (!nuevoDoc.equals(cliente.getDocumento())) {
                try {
                    Optional<Cliente> existente = clienteRepository.obtenerPorDocumento(nuevoDoc);
                    if (existente.isPresent() && !Objects.equals(existente.get().getId(), id)) {
                        throw new DatosInvalidosException("Ya existe un cliente con el documento: " + nuevoDoc);
                    }
                } catch (SQLException e) {
                    throw new DatosInvalidosException("Error al verificar documento: " + e.getMessage(), e);
                }
                cliente.setDocumento(nuevoDoc);
            }
        }

        try {
            clienteRepository.actualizar(cliente);
            return cliente;
        } catch (SQLException e) {
            throw new DatosInvalidosException("Error al actualizar cliente: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene un cliente por su identificador primario.
     *
     * @param id ID del cliente.
     * @return Cliente encontrado.
     * @throws ClienteNoEncontradoException Si no existe.
     */
    public Cliente obtenerClientePorId(int id) {
        try {
            return clienteRepository.obtenerPorId(id)
                    .orElseThrow(() -> new ClienteNoEncontradoException("Cliente con ID " + id + " no encontrado."));
        } catch (SQLException e) {
            throw new DatosInvalidosException("Error al consultar cliente: " + e.getMessage(), e);
        }
    }

    /**
     * Busca un cliente por su documento exacto.
     *
     * @param documento Documento de identidad.
     * @return Optional con el cliente si existe.
     */
    public Optional<Cliente> buscarPorDocumento(String documento) {
        if (documento == null || documento.isBlank()) {
            return Optional.empty();
        }
        try {
            return clienteRepository.obtenerPorDocumento(documento.trim());
        } catch (SQLException e) {
            throw new DatosInvalidosException("Error al buscar cliente por documento: " + e.getMessage(), e);
        }
    }

    /**
     * Búsqueda general de clientes por coincidencia parcial en nombre o documento (CU-002, RN-003).
     * Si el término es nulo o vacío, retorna todos los clientes.
     *
     * @param termino Término de búsqueda.
     * @return Lista de clientes coincidentes.
     */
    public List<Cliente> buscarClientes(String termino) {
        try {
            if (termino == null || termino.isBlank()) {
                return clienteRepository.listarTodos();
            }
            return clienteRepository.buscarGeneral(termino.trim());
        } catch (SQLException e) {
            throw new DatosInvalidosException("Error en búsqueda de clientes: " + e.getMessage(), e);
        }
    }

    /**
     * Retorna todos los clientes registrados ordenados alfabéticamente.
     *
     * @return Lista completa de clientes.
     */
    public List<Cliente> listarTodos() {
        try {
            return clienteRepository.listarTodos();
        } catch (SQLException e) {
            throw new DatosInvalidosException("Error al listar clientes: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene el historial de créditos otorgados a un cliente específico (CU-004).
     *
     * @param clienteId ID del cliente.
     * @return Lista de créditos ordenada descendentemente.
     * @throws ClienteNoEncontradoException Si el cliente no existe.
     */
    public List<Credito> obtenerCreditosCliente(int clienteId) {
        // Valida que el cliente exista
        obtenerClientePorId(clienteId);

        if (creditoRepository == null) {
            throw new IllegalStateException("CreditoRepository no fue provisto a ClienteService");
        }
        try {
            return creditoRepository.obtenerPorCliente(clienteId);
        } catch (SQLException e) {
            throw new DatosInvalidosException("Error al consultar créditos del cliente: " + e.getMessage(), e);
        }
    }
}
