# Root Access Investigation

This document summarizes the read-only investigation into whether privileged
access could be obtained on the QC4250 Android 11 head unit. The immediate
goal is not root for its own sake, but determining whether privileged access
could enable passive reading of the vendor UARTs used by the CAN/MCU stack.

## Safety policy

No boot, recovery, `vbmeta`, or other partition was written. No bootloader
unlock operation was performed, and no unreviewed privilege-escalation exploit
was executed.

Future work should preserve the following rules:

- prefer read-only inspection and reversible, RAM-only tests;
- never flash a patched image before obtaining the exact original image and a
  tested recovery path;
- do not use random binaries advertised as generic "Android 11 root" tools;
- avoid opening or transmitting through a UART while its vendor service is
  using it;
- treat all findings as specific to this unit and firmware build.

## Verified device state

The following properties and conditions were observed:

| Item | Observed value |
|---|---|
| Android version | 11 / API 30 |
| Build type | `user` |
| Build signing | `release-keys` |
| `ro.debuggable` | `1` |
| `ro.secure` | `1` |
| `ro.adb.secure` | `1` |
| Verified Boot state | `orange` |
| Flash lock property | `ro.boot.flash.locked=0` |
| vbmeta device state | `unlocked` |
| SELinux | `Permissive` |
| Android security patch | `2022-01-01` |
| Vendor security patch | `2022-01-01` |
| Kernel | `4.19.157-perf+`, built May 19, 2025 |
| Current slot | `_a` |
| Update layout | A/B with virtual A/B enabled |

The reported fingerprint is:

```text
qti/bengal/bengal:11/RKQ1.220116.001/eng.public_user.20250519.142105:user/release-keys
```

The device has `boot_a`, `boot_b`, `recovery_a`, `recovery_b`, `vbmeta_a`,
`vbmeta_b`, `dtbo_a`, `dtbo_b`, and `super` partitions.

No accessible `su` binary, Magisk installation, or KernelSU installation was
found. `adbd` runs as the `shell` user (UID 2000), despite being started with a
root SELinux label argument. A reversible `adb root` test caused ADB to restart,
but the reconnected shell still had UID 2000. It did not provide root and made
no persistent change.

## Why an Android 11 exploit is not the first choice

The old 2022 patch level means that publicly documented vulnerabilities may be
relevant, but it does not establish that this exact build is exploitable.
Vendors can backport individual fixes without changing the displayed patch
level, and successful exploitation depends on the kernel, vendor drivers,
configuration, and reachable attack surface.

There is no universal ADB command that turns an Android 11 `shell` into root.
Running an arbitrary local privilege-escalation binary could crash or corrupt
the device and offers poor assurance about what the binary itself does.

Dirty Pipe is not applicable to the observed kernel because it affects Linux
kernels beginning with version 5.8, while this unit runs 4.19.

## Recommended order of investigation

### 1. Passive audit of vendor privileged paths

This is the safest next step. It consists primarily of inspecting service
registrations, process identities, permissions, package manifests, properties,
device nodes, sockets, file capabilities, setuid files, and `init` service
definitions.

The audit would look for:

- vendor Binder services accepting calls from `adb shell` without adequate
  UID or permission checks;
- local sockets, diagnostic ports, or factory daemons running as `root` or
  `system`;
- accessible setuid programs, file capabilities, or privileged diagnostic
  binaries;
- engineering properties or factory modes that enable a diagnostic service;
- exported components in privileged system applications that can perform a
  narrowly scoped operation on behalf of the shell;
- incorrect permissions on UARTs, block devices, sysfs nodes, or related
  resources;
- `init.rc` services or triggers that can safely be invoked by the shell.

Possible outcomes are:

1. A privileged vendor path already exists and permits access to the UART, or
   allows a trusted service to read it, without granting general root.
2. A disabled factory or diagnostic mode exists and may be activatable using a
   reversible mechanism, subject to a separate risk review.
3. A potentially vulnerable component is identified. It should first be
   extracted and analyzed, then tested only with a minimal, targeted proof of
   concept.
4. No suitable path exists. This would eliminate the safest vendor shortcuts
   and justify evaluating a temporary boot approach next.

A narrowly scoped UART-reading path would be preferable to full root because
it exposes less of the system and better matches the actual investigation goal.

### 2. Temporarily boot an exact patched boot image

If the bootloader really supports it, the preferred Magisk experiment would be
`fastboot boot` using a patched copy of the **exact** boot image for this build.
This loads an image into RAM rather than immediately flashing a partition.

Before attempting it, the following must be established:

- the exact stock `boot.img` has been obtained from matching firmware, an OTA,
  a recovery/EDL readout, or another trustworthy source;
- its build and partition layout match the installed firmware;
- the bootloader accepts temporary booting rather than requiring a flash;
- the correct USB/bootloader procedure and a tested recovery route are known;
- both A/B slots and the role of `vbmeta` have been accounted for.

Extracting and patching `boot.img` would make root technically plausible, but
the current unprivileged ADB shell cannot normally read the boot block device.
Obtaining the exact image is therefore a separate prerequisite. A direct flash
is not recommended as the first test.

### 3. Targeted local privilege escalation

Only if the first two routes are unavailable should a local
privilege-escalation test be considered. It must match the exact kernel or
reachable vendor component, use reviewable source code, avoid persistence and
partition writes, and be tested with a recovery plan available.

This route is less predictable than passive auditing or temporary booting. A
successful proof should initially do only something harmless such as report
UID 0, then exit; UART access would be investigated separately.

## Current conclusion

The device appears unusually favorable for research because the bootloader
properties report an unlocked state, Verified Boot is orange, SELinux is
permissive, and `ro.debuggable` is enabled. None of those facts alone grants
root, however.

The recommended sequence is:

```text
passive vendor-path audit
  -> exact-image temporary boot assessment
  -> carefully selected privilege escalation only if necessary
```

## Da testare

  Rilievi principali:

- Critico — probabile percorso adb shell → root: il servizio init xycommand viene eseguito come root con argomento ${sys.external.xycommand}. Il binario usa system() e, per input non riconosciuti, costruisce /
    system/bin/%s. La proprietà non ha un contesto SELinux specifico rilevato e SELinux è permissivo. Una
    command injection tramite proprietà appare quindi plausibile, ma non è stata provata perché setprop
    attiverebbe realmente il servizio.

- Alto — ZLink gira senza isolamento: /system/bin/zlink5.sh, z-link e z-mdnsd girano come UID 0, dominio
    init, capability complete e senza seccomp. Lo script imposta inoltre LD_LIBRARY_PATH sulla directory
    privata dell’app ZLink: è un confine di fiducia pericoloso, anche se non direttamente scrivibile da ADB
    shell.

- Alto — AutoTest eccessivamente privilegiata: com.xyauto.android.autotest è DEBUGGABLE, usa UID condiviso
    android.uid.system, possiede SERIAL_PORT e ha cinque componenti esportati senza permesso. Tuttavia run-as
    la rifiuta proprio perché usa uno shared UID, quindi non offre una scorciatoia diretta.

- L’attività esportata MainActivity_serail apre /dev/ttyS1 e trasmette automaticamente un comando fisso:
    non va usata per un audit passivo.

- Le UART /dev/ttyHS0..2 restano system:system 0660. Non sono state aperte.
- Sono presenti listener TCP root su tutte le interfacce alle porte 1555, 3152, 7722 e 57677; non è stato
    possibile attribuirli con certezza ai processi senza privilegi ulteriori.

- Le proprietà bootloader riportate in ROOT.md:24 sono state riconfermate.

  Conclusione: prima di considerare Magisk o un LPE del kernel, xycommand è nettamente la strada più
  promettente. Il prossimo passo sarebbe un PoC minimale e reversibile che faccia scrivere soltanto id in /
  data/local/tmp, ma non l’ho eseguito perché non sarebbe più un audit puramente passivo.
