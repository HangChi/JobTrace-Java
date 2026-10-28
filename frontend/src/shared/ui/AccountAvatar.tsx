import { useState } from 'react'

export default function AccountAvatar({ name, image }: { name: string; image: string | null }) {
  const [failedImage, setFailedImage] = useState<string | null>(null)
  return <span className="avatar" aria-hidden="true">{image && image !== failedImage
    ? <img src={image} alt="" width={36} height={36} onError={() => setFailedImage(image)} />
    : name.trim().slice(0, 1) || '用'}</span>
}
