import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import {
  createUser,
  deleteUser,
  listUsers,
  resetUserPassword,
  updateUser,
  type KbUser
} from '@/api'
import { queryKeys } from '@/lib/query-keys'

export function useUsersQuery() {
  return useQuery({
    queryKey: queryKeys.users.all,
    queryFn: async () => {
      const res = await listUsers()
      return res.data || []
    }
  })
}

export function useCreateUserMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { username: string; password: string; displayName?: string; role: string }) =>
      createUser(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.users.all })
    }
  })
}

export function useUpdateUserMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<KbUser> }) => updateUser(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.users.all })
    }
  })
}

export function useDeleteUserMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteUser(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.users.all })
    }
  })
}

export function useResetUserPasswordMutation() {
  return useMutation({
    mutationFn: ({ id, newPassword }: { id: number; newPassword: string }) =>
      resetUserPassword(id, newPassword)
  })
}
