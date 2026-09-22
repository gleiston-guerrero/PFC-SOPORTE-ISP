import { useState, type CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
import { assignTechnician, updateTicketStatus } from '../api/ticketsApi'
import { CheckIcon } from '../../../components/icons'
import type { Role } from '../../auth/session'
import type { UserResponse } from '../../admin/types/user'
import type { TicketResponse, TicketStatus } from '../types/ticket'

const STATUSES: TicketStatus[] = ['NUEVO', 'ASIGNADO', 'EN_PROGRESO', 'ESCALADO', 'RESUELTO', 'CERRADO']

// RESUELTO exige foto + GPS de evidencia cuando quien cierra es TECNICO
// (TicketAuthorization/UpdateTicketStatusHandler.java, Entregable 10 de la guia de cierre) --
// esta consola no captura camara ni geolocalizacion, asi que ofrecerle RESUELTO a un TECNICO
// aqui solo terminaria en un 400 del backend (revision externa posterior encontro exactamente
// este caso: la consola ofrecia RESUELTO a cualquier rol y el PATCH se mandaba sin evidencia).
// El cierre en sitio es una capacidad de apps/mobile a proposito; un ADMIN si puede seguir
// resolviendo un ticket sin evidencia desde aqui (alta administrativa, mismo criterio que el
// backend).
const STATUSES_SIN_EVIDENCIA = STATUSES.filter((s) => s !== 'RESUELTO')

/**
 * Solo TECNICO/ADMIN la ven (ver ConsolePage.tsx) -- CLIENTE nunca puede cambiar estado ni
 * asignar (403 del backend, ver TicketAuthorization.assertCanManage).
 *
 * La asignacion es distinta segun quien mira:
 * - TECNICO: "Asignarme" -- autoasignacion con su propio userId, sin selector (un tecnico
 *   solo se asigna tickets a si mismo dentro de su zona).
 * - ADMIN: selector "Asignar a tecnico..." -- un admin no es tecnico, asi que autoasignarse
 *   (como hacia antes con el mismo boton) violaba la FK tickets_technician_id_fkey igual que
 *   el bug de sincronizacion que se arreglo hoy. Se le ofrece la lista real de tecnicos de la
 *   zona del ticket (ver useTechnicians.ts, reutiliza GET /api/v1/auth/admin/users).
 */
export function TicketRowActions({
  ticket,
  currentUserId,
  role,
  technicians,
  onChanged,
}: {
  ticket: TicketResponse
  currentUserId: string | null
  role: Role | null
  technicians: UserResponse[]
  onChanged: () => void
}) {
  const { t } = useTranslation()
  const [busy, setBusy] = useState(false)

  const handleStatusChange = async (status: TicketStatus) => {
    if (status === ticket.status) return
    setBusy(true)
    try {
      await updateTicketStatus(ticket.ticketId, status)
      onChanged()
    } finally {
      setBusy(false)
    }
  }

  const handleAssignToMe = async () => {
    if (!currentUserId) return
    setBusy(true)
    try {
      await assignTechnician(ticket.ticketId, currentUserId)
      onChanged()
    } finally {
      setBusy(false)
    }
  }

  const handleAssignTo = async (technicianId: string) => {
    if (!technicianId) return
    setBusy(true)
    try {
      await assignTechnician(ticket.ticketId, technicianId)
      onChanged()
    } finally {
      setBusy(false)
    }
  }

  const zoneTechnicians = technicians.filter((tech) => tech.zone === ticket.zone)

  // Un TECNICO no ve RESUELTO como destino (debe cerrar en sitio desde apps/mobile, con
  // evidencia) -- salvo que el ticket YA este en RESUELTO, para que el <select> siga
  // mostrando su estado real en vez de caer a la primera opcion de la lista.
  const availableStatuses =
    role === 'TECNICO' && ticket.status !== 'RESUELTO' ? STATUSES_SIN_EVIDENCIA : STATUSES

  return (
    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
      <select
        value={ticket.status}
        disabled={busy}
        onChange={(e) => handleStatusChange(e.target.value as TicketStatus)}
        className="field-input"
        style={selectStyle}
      >
        {availableStatuses.map((s) => (
          <option key={s} value={s}>
            {t(`status.${s}`)}
          </option>
        ))}
      </select>

      {!ticket.technicianId && role === 'TECNICO' && (
        <button
          type="button"
          disabled={busy}
          onClick={handleAssignToMe}
          className="assign-btn"
          style={assignButtonStyle}
          aria-label={t('console.actions.assignToMe')}
          title={t('console.actions.assignToMe')}
        >
          <CheckIcon size={14} />
        </button>
      )}

      {!ticket.technicianId && role === 'ADMIN' && zoneTechnicians.length > 0 && (
        <select
          value=""
          disabled={busy}
          onChange={(e) => handleAssignTo(e.target.value)}
          className="field-input"
          style={{ ...selectStyle, maxWidth: 160 }}
          aria-label={t('console.actions.assignTo')}
        >
          <option value="">{t('console.actions.assignTo')}</option>
          {zoneTechnicians.map((tech) => (
            <option key={tech.id} value={tech.id}>
              {tech.fullName}
            </option>
          ))}
        </select>
      )}

      {!ticket.technicianId && role === 'ADMIN' && zoneTechnicians.length === 0 && (
        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontStyle: 'italic' }}>
          {t('console.actions.noTechniciansInZone')}
        </span>
      )}
    </div>
  )
}

const selectStyle: CSSProperties = {
  padding: '4px 8px',
  borderRadius: 6,
  border: '1px solid var(--border)',
  background: 'var(--surface)',
  color: 'var(--text)',
  fontSize: '0.8rem',
}

const assignButtonStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 28,
  height: 28,
  padding: 0,
  borderRadius: '50%',
  border: '1px solid var(--color-teal)',
  background: 'transparent',
  color: 'var(--color-teal)',
  cursor: 'pointer',
  flexShrink: 0,
}
