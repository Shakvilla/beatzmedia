import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../tokens.dart';
import '../../features/discovery/data/dto/catalog_dto.dart';

class CatalogCard extends StatelessWidget {
  const CatalogCard(
      {super.key,
      required this.title,
      required this.subtitle,
      required this.image,
      this.ownership,
      this.price,
      this.downloadable = false,
      this.onTap});
  final String title;
  final String subtitle;
  final String image;
  final String? ownership;
  final MoneyDto? price;
  final bool downloadable;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 140,
        decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(AppRadii.md),
            border: Border.all(color: AppColors.border)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          ClipRRect(
            borderRadius:
                const BorderRadius.vertical(top: Radius.circular(AppRadii.md)),
            child: CachedNetworkImage(
                imageUrl: image,
                height: 120,
                width: 140,
                fit: BoxFit.cover,
                placeholder: (_, __) =>
                    Container(height: 120, color: AppColors.surfaceElevated),
                errorWidget: (_, __, ___) => Container(
                    height: 120,
                    color: AppColors.surfaceElevated,
                    child: const Icon(Icons.music_note,
                        color: AppColors.textMuted))),
          ),
          Padding(
            padding: const EdgeInsets.all(8),
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title,
                  style: AppTypography.title.copyWith(fontSize: 13),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  semanticsLabel: title),
              const SizedBox(height: 2),
              Text(subtitle,
                  style: AppTypography.mono,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis),
              const SizedBox(height: 6),
              Row(children: [
                if (ownership != null) _badge(ownership!, price),
                const Spacer(),
                if (downloadable)
                  const Icon(Icons.download_done_rounded,
                      size: 14, color: AppColors.gold),
              ]),
            ]),
          ),
        ]),
      ),
    );
  }

  Widget _badge(String ownership, MoneyDto? price) {
    final isOwned = ownership == 'owned';
    final isFree = ownership == 'free';
    final label = isOwned
        ? 'OWNED'
        : isFree
            ? 'FREE'
            : (price != null ? 'GHS ${price.amount}' : 'FOR SALE');
    final color = isOwned
        ? AppColors.gold
        : isFree
            ? AppColors.success
            : AppColors.primary;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
          color: color, borderRadius: BorderRadius.circular(AppRadii.pill)),
      child: Text(label,
          style: const TextStyle(
              fontFamily: AppTypography.monoFamily,
              fontSize: 9,
              fontWeight: FontWeight.w700,
              color: Colors.white)),
    );
  }
}

class SectionHeader extends StatelessWidget {
  const SectionHeader(
      {super.key, required this.title, this.subtitle, this.onSeeAll});
  final String title;
  final String? subtitle;
  final VoidCallback? onSeeAll;
  @override
  Widget build(BuildContext context) {
    return Row(children: [
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title, style: AppTypography.displayMedium.copyWith(fontSize: 18)),
        if (subtitle != null) Text(subtitle!, style: AppTypography.mono),
      ]),
      const Spacer(),
      if (onSeeAll != null)
        TextButton(
            onPressed: onSeeAll,
            child: const Text('See all',
                style: TextStyle(color: AppColors.primary))),
    ]);
  }
}
