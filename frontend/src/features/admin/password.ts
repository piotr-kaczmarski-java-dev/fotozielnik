// no 0/O, 1/l/I: the admin reads it out or writes it down for the user
const ALPHABET = 'abcdefghjkmnpqrstuvwxyz23456789'

export function generatePassword(length = 10): string {
  const bytes = crypto.getRandomValues(new Uint8Array(length))
  return Array.from(bytes, (b) => ALPHABET[b % ALPHABET.length]).join('')
}
